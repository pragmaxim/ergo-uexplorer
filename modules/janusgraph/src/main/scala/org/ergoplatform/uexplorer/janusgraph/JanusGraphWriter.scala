package org.ergoplatform.uexplorer.janusgraph

import com.typesafe.scalalogging.LazyLogging
import org.apache.tinkerpop.gremlin.structure.{Graph, T, Vertex}
import org.ergoplatform.uexplorer.*
import org.ergoplatform.uexplorer.db.{BestBlockInserted, FullBlock, NormalizedBlock}
import org.janusgraph.core.Multiplicity
import zio.*
import scala.collection.immutable.{ArraySeq, TreeMap}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait JanusGraphWriter extends LazyLogging {
  this: JanusGraphBackend =>

  def initGraph: Boolean = {
    val mgmt = janusGraph.openManagement()
    if (!mgmt.containsEdgeLabel("from")) {
      logger.info("Creating Janus properties, indexes and labels")
      // tx -> address edges
      mgmt.makeEdgeLabel("from").unidirected().multiplicity(Multiplicity.SIMPLE).make()
      mgmt.makeEdgeLabel("to").multiplicity(Multiplicity.SIMPLE).make()
      mgmt.makePropertyKey("value").dataType(classOf[java.lang.Long]).make()

      // addresses
      mgmt.makeVertexLabel("address").make()
      mgmt.makePropertyKey("address").dataType(classOf[String]).make()

      // transactions
      mgmt.makeVertexLabel("txId").make()
      mgmt.makePropertyKey("txId").dataType(classOf[String]).make()
      mgmt.makePropertyKey("height").dataType(classOf[Integer]).make()
      mgmt.makePropertyKey("timestamp").dataType(classOf[java.lang.Long]).make()
      mgmt.commit()
      true
    } else {
      logger.info("Janus graph already initialized...")
      false
    }
  }

  def writeTxsAndCommit(block: BestBlockInserted): Task[BestBlockInserted] = ZIO.attempt {
    blocks.iterator
      .foreach { case BestBlockInserted(b, _) =>
        b.inputRecords.byTxId.foreach { case (txId, inputRecords) =>
          val outputRecords = b.outputRecords.byErgoTree.values.flatten.filter(_.txId == txId)
          val fixMe         = mutable.Map.empty[ErgoTreeHex, mutable.Map[BoxId, Value]] // TODO
          TxGraphWriter.writeGraph(txId, b.block.height, b.block.timestamp, fixMe, outputRecords)(janusGraph)

        }
      }
    janusGraph.tx().commit()
    blocks
  }
}
