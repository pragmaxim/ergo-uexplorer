package org.ergoplatform.uexplorer.indexer.chain

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.uexplorer.Const.Protocol.{Emission, Foundation}
import org.ergoplatform.uexplorer.backend.Repo
import org.ergoplatform.uexplorer.chain.{BlockProcessor, ChainLinker, ChainTip}
import org.ergoplatform.uexplorer.db.*
import org.ergoplatform.uexplorer.indexer.chain.StreamExecutor.ChainSyncResult
import org.ergoplatform.uexplorer.indexer.config.ChainIndexerConf
import org.ergoplatform.uexplorer.node.ApiFullBlock
import org.ergoplatform.uexplorer.{illEx, BoxId, CoreConf, ReadableStorage, UnexpectedStateError, WritableStorage}
import zio.*
import zio.Exit.{Failure, Success}
import zio.stream.{ZSink, ZStream}

case class BlockWriter(
  storage: WritableStorage,
  repo: Repo,
  graphBackend: GraphBackend,
  chainIndexerConf: ChainIndexerConf
) {

  implicit private val ps: CoreConf            = chainIndexerConf.core
  implicit private val enc: ErgoAddressEncoder = ps.addressEncoder

  private def hasParentAndIsChained(fork: List[LinkedBlock]): Boolean =
    fork.size > 1 && storage.containsBlock(fork.head.block.parentId, fork.head.block.height - 1) &&
      fork.sliding(2).forall {
        case first :: second :: Nil =>
          first.b.header.id == second.block.parentId
        case _ =>
          false
      }

  private def rollbackFork(winFork: List[LinkedBlock]): Task[ForkInserted] =
    if (!hasParentAndIsChained(winFork)) {
      ZIO.fail(
        new UnexpectedStateError(
          s"Inserting fork ${winFork.map(_.b.header.id).mkString(",")} at height ${winFork.map(_.block.height).mkString(",")} illegal"
        )
      )
    } else {
      for {
        _                 <- ZIO.log(s"Adding winning fork ${winFork.map(b => s"${b.block.blockId} @ ${b.block.height}").mkString("\n", "\n", "")}")
        preForkVersionOpt <- ZIO.attempt(storage.getBlocksByHeight(winFork.head.b.header.height).map(_._2.revision).headOption)
        loosingFork = winFork.flatMap(b => storage.getBlocksByHeight(b.block.height).filter(_._1 != b.b.header.id))
        _ <- ZIO.log(s"Rolling back loosing fork at version $preForkVersionOpt : ${loosingFork.map(b => s"${b._1} @ ${b._2.height}").mkString("\n", "\n", "")}")
        preForkVersion <- ZIO.getOrFailWith(illEx(s"Block ${winFork.head.b.header.id} @ ${winFork.head.b.header.height} not persisted!"))(preForkVersionOpt)
        _              <- storage.rollbackTo(preForkVersion)
      } yield ForkInserted(winFork, loosingFork)
    }

  def insertBranchFlow(
    source: ZStream[Any, Throwable, ApiFullBlock],
    chainLinker: ChainLinker
  ): Task[ChainSyncResult] =
    source
      .via(BlockProcessor.processingFlow(chainLinker))
      .mapConcatZIO {
        case Nil =>
          ZIO.fail(new IllegalStateException("ChainLinker cannot return no block"))
        case bestBlock :: Nil =>
          persistBlock(bestBlock).map(_ :: Nil)
        case winningFork =>
          for {
            forkInserted <- rollbackFork(winningFork)
            _            <- ZIO.log(s"Removing old fork : ${forkInserted.loosingFork.map(b => s"${b._1} @ ${b._2.height}").mkString("\n", "\n", "")}")
            _            <- repo.removeBlocks(forkInserted.loosingFork.map(_._1))
            _            <- ZIO.log(s"Adding new fork : ${winningFork.map(b => s"${b.b.header.id} @ ${b.block.height}").mkString("\n", "\n", "")}")
            fork         <- ZIO.foreach(forkInserted.winningFork)(persistBlock)
          } yield fork
      }
      .tap { b =>
        if (b.linkedBlock.block.height % chainIndexerConf.mvStore.heightCompactRate == 0)
          storage.writeReportAndCompact(indexing = true)
        else ZIO.succeed(())
      }
      .tap { block =>
        graphBackend.writeTxsAndCommit(block)
      }
      .run(
        ZSink.foldLeft[BestBlockInserted, Option[(BestBlockInserted, Int)]](Option.empty) {
          case (Some(last, count), in) =>
            Some((in, count + 1))
          case (None, in) =>
            Some((in, 1))
        }
      )
      .onExit {
        case Success(Some((lastBlock, indexCount))) =>
          ZIO.log(s"Writing report after block at height ${lastBlock.linkedBlock.block.height}, indexed $indexCount blocks ...") *> storage
            .writeReportAndCompact(false)
            .orElseSucceed(())
        case Success(None) =>
          ZIO.log(s"Stream finished without processing any blocks")
        case Failure(cause) =>
          ZIO.logErrorCause(s"Stream failed !", cause) *> storage.writeReportAndCompact(false).orElseSucceed(())
      }
      .map { lastBlock =>
        ChainSyncResult(
          lastBlock.map(_._1),
          storage.asInstanceOf[ReadableStorage],
          graphBackend.graphTraversalSource
        )
      }

  private def persistBlock(b: LinkedBlock): Task[BestBlockInserted] = {
    val inputIds: Seq[BoxId] =
      b.b.transactions.transactions
        .flatMap(_.inputs.collect { case i if i.boxId != Emission.inputBox && i.boxId != Foundation.inputBox => i.boxId })

    def storageOps = List(
      storage.persistErgoTreeByUtxo(b.outputRecords.byErgoTree) *> storage.removeInputBoxesByErgoTree(inputIds),
      storage.persistErgoTreeT8ByUtxo(b.outputRecords.byErgoTreeT8) *> storage.removeInputBoxesByErgoTreeT8(inputIds),
      storage.persistUtxosByTokenId(b.outputRecords.utxosByTokenId) *> storage.persistTokensByUtxo(b.outputRecords.tokensByUtxo) *> storage
        .removeInputBoxesByTokenId(inputIds),
      storage.insertNewBlock(b.b.header.id, b.block, storage.getCurrentRevision)
    )

    for _ <- (ZIO.collectAllParDiscard(storageOps) *> storage.commit()) <&> repo.writeBlock(b, inputIds)
    yield BestBlockInserted(b, None)
  }
}

object BlockWriter {
  def layer: ZLayer[
    WritableStorage with Repo with GraphBackend with ChainIndexerConf,
    Nothing,
    BlockWriter
  ] =
    ZLayer.fromFunction(BlockWriter.apply _)

}
