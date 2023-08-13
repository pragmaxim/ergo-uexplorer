package org.ergoplatform.uexplorer.indexer

import org.ergoplatform.uexplorer.backend.blocks.{BlockRepo, PersistentBlockRepo}
import org.ergoplatform.uexplorer.backend.boxes.PersistentBoxRepo
import org.ergoplatform.uexplorer.backend.{H2Backend, PersistentRepo}
import org.ergoplatform.uexplorer.db.GraphBackend
import org.ergoplatform.uexplorer.http.*
import org.ergoplatform.uexplorer.indexer.chain.*
import org.ergoplatform.uexplorer.indexer.config.ChainIndexerConf
import org.ergoplatform.uexplorer.indexer.db.{Backend, GraphBackend}
import org.ergoplatform.uexplorer.indexer.mempool.{MemPool, MempoolSyncer}
import org.ergoplatform.uexplorer.indexer.plugin.PluginManager
import org.ergoplatform.uexplorer.storage.{MvStorage, MvStoreConf}
import zio.*
import zio.logging.LogFormat
import zio.logging.backend.SLF4J

import scala.language.postfixOps

object ChainIndexer extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogFormat.colored)

  def run =
    (for {
      serverFiber   <- Backend.runServer
      nodePoolFiber <- ZIO.serviceWithZIO[StreamScheduler](_.validateAndSchedule())
      done = serverFiber.zip(nodePoolFiber)
    } yield done).provide(
      ChainIndexerConf.layer,
      NodePoolConf.layer,
      MvStoreConf.layer,
      MemPool.layer,
      NodePool.layer,
      UnderlyingBackend.layer,
      SttpNodePoolBackend.layer,
      H2Backend.layer,
      MetadataHttpClient.layer,
      BlockHttpClient.layer,
      PersistentBlockRepo.layer,
      PersistentBoxRepo.layer,
      PersistentRepo.layer,
      StreamScheduler.layer,
      PluginManager.layer,
      StreamExecutor.layer,
      MempoolSyncer.layer,
      Initializer.layer,
      BlockReader.layer,
      BlockWriter.layer,
      GraphBackend.layer,
      MvStorage.zlayerWithDefaultDir
    )
}
