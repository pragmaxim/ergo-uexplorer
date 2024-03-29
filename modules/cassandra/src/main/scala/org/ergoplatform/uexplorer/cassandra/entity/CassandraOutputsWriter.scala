package org.ergoplatform.uexplorer.cassandra.entity

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.datastax.oss.driver.api.core.cql.{BoundStatement, DefaultBatchType, PreparedStatement}
import eu.timepit.refined.auto.*
import org.ergoplatform.uexplorer.Const
import org.ergoplatform.uexplorer.cassandra.CassandraBackend
import org.ergoplatform.uexplorer.db.{BestBlockInserted, FullBlock}

import scala.collection.immutable.ArraySeq

trait CassandraOutputsWriter { this: CassandraBackend =>
  import Outputs.*

  def outputsWriteFlow(parallelism: Int): Flow[BestBlockInserted, BestBlockInserted, NotUsed] =
    storeBatchFlow(
      parallelism,
      batchType = DefaultBatchType.LOGGED,
      buildInsertStatement(columns, node_outputs_table),
      outputInsertBinder
    )

  protected[cassandra] def outputInsertBinder: (BestBlockInserted, PreparedStatement) => ArraySeq[BoundStatement] = {
    case (BestBlockInserted(_, Some(block)), statement) =>
      block.outputs.map { output =>
        statement
          .bind()
          // format: off
          .setString(header_id,                   output.headerId)
          .setString(box_id,                      output.boxId.unwrapped)
          .setString(tx_id,                       output.txId.unwrapped)
          .setShort(tx_idx,                       output.txIndex)
          .setLong(value,                         output.value)
          .setString(address,                     output.address)
          .setInt(creation_height,                output.creationHeight)
          .setInt(settlement_height,              output.settlementHeight)
          .setShort(idx,                            output.index)
          .setLong(global_index,                  output.globalIndex)
          .setString(ergo_tree,                   output.ergoTreeHex)
          .setString(ergo_tree_template_hash,     output.ergoTreeT8HashHex)
          .setLong(timestamp,                     output.timestamp)
          .setBoolean(main_chain,                 output.mainChain)
        // format: on
      }
    case _ =>
      throw new IllegalStateException("Backend must be enabled")

  }
}

object Outputs {
  protected[cassandra] val node_outputs_table = "node_outputs"

  protected[cassandra] val box_id                  = "box_id"
  protected[cassandra] val tx_id                   = "tx_id"
  protected[cassandra] val tx_idx                  = "tx_idx"
  protected[cassandra] val header_id               = "header_id"
  protected[cassandra] val value                   = "value"
  protected[cassandra] val creation_height         = "creation_height"
  protected[cassandra] val settlement_height       = "settlement_height"
  protected[cassandra] val idx                     = "idx"
  protected[cassandra] val global_index            = "global_index"
  protected[cassandra] val ergo_tree               = "ergo_tree"
  protected[cassandra] val ergo_tree_template_hash = "ergo_tree_template_hash"
  protected[cassandra] val address                 = "address"
  protected[cassandra] val timestamp               = "timestamp"
  protected[cassandra] val main_chain              = "main_chain"

  protected[cassandra] val columns = Seq(
    box_id,
    tx_id,
    tx_idx,
    header_id,
    value,
    creation_height,
    settlement_height,
    idx,
    global_index,
    ergo_tree,
    ergo_tree_template_hash,
    address,
    timestamp,
    main_chain
  )
}
