package org.ergoplatform.uexplorer.indexer.progress

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.pattern.StatusReply
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.ergoplatform.explorer.BlockId
import org.ergoplatform.explorer.indexer.models.FlatBlock
import org.ergoplatform.explorer.protocol.models.ApiFullBlock
import org.ergoplatform.explorer.settings.ProtocolSettings
import org.ergoplatform.uexplorer.indexer.progress.ProgressState._

import scala.collection.immutable.TreeMap
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class ProgressMonitor(implicit protocol: ProtocolSettings) extends LazyLogging {
  import ProgressMonitor._

  def initialBehavior: Behavior[MonitorRequest] =
    Behaviors.setup[MonitorRequest] { _ =>
      initialized(ProgressState(TreeMap.empty, TreeMap.empty, BlockCache(Map.empty, TreeMap.empty)))
    }

  def initialized(p: ProgressState): Behaviors.Receive[MonitorRequest] =
    Behaviors.receiveMessage[MonitorRequest] {
      case InsertBestBlock(bestBlock, replyTo) =>
        p.insertBestBlock(bestBlock) match {
          case Success((bestBlockInserted, newProgress)) =>
            replyTo ! StatusReply.success(bestBlockInserted)
            initialized(newProgress)
          case Failure(ex) =>
            val h = bestBlock.header
            logger.warn(s"Unexpected insert ${h.id} at ${h.height}, parent ${h.parentId} : $p", ex)
            replyTo ! StatusReply.error(ex)
            Behaviors.same
        }
      case InsertWinningFork(fork, replyTo) =>
        p.insertWinningFork(fork) match {
          case Success((winningForkInserted, newProgress)) =>
            replyTo ! StatusReply.success(winningForkInserted)
            initialized(newProgress)
          case Failure(ex) =>
            val h = fork.head.header
            logger.warn(
              s"Unexpected fork size ${fork.size} starting ${h.id} at ${h.height}, parent ${h.parentId} : $p",
              ex
            )
            replyTo ! StatusReply.error(ex)
            Behaviors.same
        }
      case GetBlock(blockId, replyTo) =>
        replyTo ! CachedBlock(p.blockCache.byId.get(blockId))
        Behaviors.same
      case UpdateChainState(persistedEpochIndexes, replyTo) =>
        val newProgress = p.updateState(persistedEpochIndexes)
        replyTo ! newProgress
        initialized(newProgress)
      case GetChainState(replyTo) =>
        replyTo ! p
        Behaviors.same
      case FinishEpoch(epochIndex, replyTo) =>
        val (maybeNewEpoch, newProgress) = p.getFinishedEpoch(epochIndex)
        logger.info(s"$maybeNewEpoch, $newProgress")
        replyTo ! maybeNewEpoch
        initialized(newProgress)
    }
}

object ProgressMonitor {

  implicit private val timeout: Timeout = 3.seconds

  /** REQUEST */
  sealed trait MonitorRequest

  sealed trait Insertable extends MonitorRequest {
    def replyTo: ActorRef[StatusReply[Inserted]]
  }

  case class InsertBestBlock(block: ApiFullBlock, replyTo: ActorRef[StatusReply[Inserted]]) extends Insertable

  case class InsertWinningFork(blocks: List[ApiFullBlock], replyTo: ActorRef[StatusReply[Inserted]]) extends Insertable

  case class GetBlock(blockId: BlockId, replyTo: ActorRef[CachedBlock]) extends MonitorRequest

  case class UpdateChainState(lastBlockByEpochIndex: TreeMap[Int, BlockInfo], replyTo: ActorRef[ProgressState])
    extends MonitorRequest

  case class GetChainState(replyTo: ActorRef[ProgressState]) extends MonitorRequest

  case class FinishEpoch(epochIndex: Int, replyTo: ActorRef[MaybeNewEpoch]) extends MonitorRequest

  /** RESPONSE */
  sealed trait MonitorResponse

  case class CachedBlock(block: Option[BlockInfo]) extends MonitorResponse

  sealed trait Inserted extends MonitorResponse

  case class BestBlockInserted(flatBlock: FlatBlock) extends Inserted

  case class ForkInserted(newFork: List[FlatBlock], supersededFork: List[BlockInfo]) extends Inserted

  sealed trait MaybeNewEpoch extends MonitorResponse

  case class NewEpochCreated(epoch: Epoch) extends MaybeNewEpoch {

    override def toString: String =
      s"New epoch ${epoch.index} created"
  }

  case class NewEpochFailed(epochCandidate: InvalidEpochCandidate) extends MaybeNewEpoch {

    override def toString: String =
      s"New epoch ${epochCandidate.epochIndex} failed due to : ${epochCandidate.error}"
  }

  case class NewEpochExisted(epochIndex: Int) extends MaybeNewEpoch {

    override def toString: String =
      s"New epoch $epochIndex existed"
  }

  /** API */
  import akka.actor.typed.scaladsl.AskPattern._

  def insertWinningFork(
    winningFork: List[ApiFullBlock]
  )(implicit s: ActorSystem[Nothing], ref: ActorRef[MonitorRequest]): Future[Inserted] =
    ref.askWithStatus(ref => InsertWinningFork(winningFork, ref))

  def insertBestBlock(
    bestBlock: ApiFullBlock
  )(implicit s: ActorSystem[Nothing], ref: ActorRef[MonitorRequest]): Future[Inserted] =
    ref.askWithStatus(ref => InsertBestBlock(bestBlock, ref))

  def getBlock(blockId: BlockId)(implicit s: ActorSystem[Nothing], ref: ActorRef[MonitorRequest]): Future[CachedBlock] =
    ref.ask(ref => GetBlock(blockId, ref))

  def updateState(
    lastBlockByEpochIndex: TreeMap[Int, BlockInfo]
  )(implicit s: ActorSystem[Nothing], ref: ActorRef[MonitorRequest]): Future[ProgressState] =
    ref.ask(ref => UpdateChainState(lastBlockByEpochIndex, ref))

  def getChainState(implicit s: ActorSystem[Nothing], ref: ActorRef[MonitorRequest]): Future[ProgressState] =
    ref.ask(ref => GetChainState(ref))

  def finishEpoch(
    epochIndex: Int
  )(implicit s: ActorSystem[Nothing], ref: ActorRef[MonitorRequest]): Future[MaybeNewEpoch] =
    ref.ask(ref => FinishEpoch(epochIndex, ref))
}
