package org.ergoplatform.uexplorer.indexer

import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.{KillSwitches, SharedKillSwitch}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import org.ergoplatform.uexplorer.db.Block
import org.ergoplatform.uexplorer.indexer.api.{Backend, GraphBackend, InMemoryBackend}
import org.ergoplatform.uexplorer.indexer.cassandra.CassandraBackend
import org.ergoplatform.uexplorer.indexer.chain.*
import org.ergoplatform.uexplorer.indexer.chain.ChainIndexer.ChainSyncResult
import org.ergoplatform.uexplorer.indexer.chain.ChainLoader.{ChainValid, MissingEpochs}
import org.ergoplatform.uexplorer.indexer.chain.ChainStateHolder.*
import org.ergoplatform.uexplorer.indexer.config.{CassandraDb, ChainIndexerConf, InMemoryDb}
import org.ergoplatform.uexplorer.indexer.mempool.MempoolStateHolder.*
import org.ergoplatform.uexplorer.indexer.mempool.{MempoolStateHolder, MempoolSyncer}
import org.ergoplatform.uexplorer.indexer.plugin.PluginManager
import org.ergoplatform.uexplorer.indexer.utxo.{DiskUtxoSnapshotManager, UtxoState}
import org.ergoplatform.uexplorer.plugin.Plugin
import org.ergoplatform.uexplorer.plugin.Plugin.{UtxoStateWithPool, UtxoStateWithoutPool}
import org.ergoplatform.uexplorer.{Address, BoxId, Const}
import org.slf4j.LoggerFactory

import java.io.{PrintWriter, StringWriter}
import java.util.ServiceLoader
import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import org.ergoplatform.uexplorer.ProtocolSettings
import org.ergoplatform.uexplorer.AkkaStreamSupport
import org.ergoplatform.uexplorer.http.BlockHttpClient
import org.ergoplatform.uexplorer.indexer.http.Routes
import org.ergoplatform.uexplorer.http.LocalNodeUriMagnet
import org.ergoplatform.uexplorer.http.RemoteNodeUriMagnet

object Application extends App with AkkaStreamSupport {
  ChainIndexerConf.loadWithFallback match {
    case Left(failures) =>
      failures.toList.foreach(f => println(s"Config error ${f.description} at ${f.origin}"))
      System.exit(1)
    case Right((conf, config)) =>
      val guardian: Behavior[Nothing] =
        Behaviors.setup[Nothing] { implicit ctx =>
          implicit val system: ActorSystem[Nothing] = ctx.system
          implicit val protocol: ProtocolSettings   = conf.protocol
          implicit val chainStateHolderRef: ActorRef[ChainStateHolderRequest] =
            ctx.spawn(new ChainStateHolder().initialBehavior, "ChainStateHolder")
          implicit val mempoolStateHolderRef: ActorRef[MempoolStateHolderRequest] =
            ctx.spawn(MempoolStateHolder.behavior(MempoolState.empty), "MempoolStateHolder")

          implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("uexplorer-kill-switch")
          implicit val localNodeUriMagnet: LocalNodeUriMagnet = conf.localUriMagnet
          implicit val remoteNodeUriMagnet: RemoteNodeUriMagnet = conf.remoteUriMagnet
          
          val bindingFuture = Http().newServerAt("localhost", 8089).bind(new Routes().shutdown)
          CoordinatedShutdown(system).addTask(
            CoordinatedShutdown.PhaseBeforeServiceUnbind,
            "stop-akka-streams"
          ) { () =>
            for {
              _       <- Future(killSwitch.shutdown())
              binding <- bindingFuture
              done    <- binding.unbind()
            } yield done
          }

          val initializationF =
            for {
              blockHttpClient   <- BlockHttpClient.withNodePoolBackend
              pluginManager     <- PluginManager.initialize
              backend           <- Future.fromTry(Backend(conf.backendType))
              graphBackend      <- Future.fromTry(GraphBackend(conf.graphBackendType))
              snapshotManager   = new DiskUtxoSnapshotManager()
              chainIndexer      = new ChainIndexer(backend, graphBackend, blockHttpClient, snapshotManager)
              mempoolSyncer     = new MempoolSyncer(blockHttpClient)
              chainLoader       = new ChainLoader(backend, graphBackend, snapshotManager)
              scheduler         = new Scheduler(pluginManager, chainIndexer, mempoolSyncer, chainLoader)
              done              <- scheduler.validateAndSchedule(0.seconds, 5.seconds)
            } yield done

          initializationF.andThen {
            case Failure(ex) =>
              val sw = new StringWriter()
              ex.printStackTrace(new PrintWriter(sw))
              println(s"Shutting down due to unexpected error:\n$sw")
              CoordinatedShutdown.get(system).run(CoordinatedShutdown.ActorSystemTerminateReason)
            case Success(_) =>
              println("Chain indexer should never stop successfully")
              CoordinatedShutdown.get(system).run(CoordinatedShutdown.ActorSystemTerminateReason)
          }
          Behaviors.same
        }
      val system: ActorSystem[Nothing] = ActorSystem[Nothing](guardian, "uexplorer", config)
      Await.result(system.whenTerminated, Duration.Inf)
  }

}
