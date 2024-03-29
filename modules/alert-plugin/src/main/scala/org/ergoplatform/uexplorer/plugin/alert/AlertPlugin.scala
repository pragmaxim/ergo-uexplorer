package org.ergoplatform.uexplorer.plugin.alert

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.{DiscordClient, GatewayDiscordClient}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.ergoplatform.uexplorer.node.ApiTransaction
import org.ergoplatform.uexplorer.plugin.Plugin
import org.ergoplatform.uexplorer.*
import org.ergoplatform.uexplorer.db.{BestBlockInserted, FullBlock}
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.{Flux, Mono}
import zio.Task

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.util.Try

class AlertPlugin extends Plugin {
  protected val logger: Logger            = LoggerFactory.getLogger(getClass.getName)
  private lazy val discord: Task[Discord] = Discord.fromEnv

  private lazy val detectors = List(
    new HighValueDetector(3 * 1000, 10 * 1000)
  )

  private lazy val trackers = List(
    new SourceAnalyzer()
  )

  def name: String = "Alert Plugin"

  def init: Task[Unit] = discord.unit

  def close: Task[Unit] = discord.flatMap(_.logout)

  def processMempoolTx(
    newTx: ApiTransaction,
    utxoState: ReadableStorage,
    graphTraversalSource: GraphTraversalSource
  ): Task[Unit] =
    discord.flatMap { c =>
      c.sendMessages(
        detectors.flatMap { detector =>
          detector
            .inspectNewPoolTx(newTx, utxoState, graphTraversalSource)
            .flatMap { txMatch =>
              trackers.flatMap(
                _.trackTx(txMatch, utxoState, graphTraversalSource).toList
                  .map(_.toString)
              )
            }
        }
      )
    }

  def processNewBlock(
    newBlock: BestBlockInserted,
    utxoState: ReadableStorage,
    graphTraversalSource: GraphTraversalSource
  ): Task[Unit] =
    discord.flatMap { c =>
      c.sendMessages(
        detectors.flatMap { detector =>
          detector
            .inspectNewBlock(newBlock, utxoState, graphTraversalSource)
            .flatMap { blockMatch =>
              trackers.flatMap(
                _.trackBlock(blockMatch, utxoState, graphTraversalSource).toList.map(_.toString)
              )
            }

        }
      )
    }
}
