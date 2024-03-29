package org.ergoplatform.uexplorer.cassandra.entity

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.datastax.oss.driver.api.core.cql.{BoundStatement, DefaultBatchType, PreparedStatement}
import com.typesafe.scalalogging.LazyLogging
import eu.timepit.refined.auto.*
import org.ergoplatform.uexplorer.cassandra.CassandraBackend
import org.ergoplatform.uexplorer.db.{BestBlockInserted, FullBlock}

import scala.collection.immutable.ArraySeq

trait CassandraRegistersWriter extends LazyLogging {
  this: CassandraBackend =>

  import Registers.*

  def registersWriteFlow(parallelism: Int): Flow[BestBlockInserted, BestBlockInserted, NotUsed] =
    storeBatchFlow(
      parallelism,
      batchType = DefaultBatchType.LOGGED,
      buildInsertStatement(columns, node_registers_table),
      registersInsertBinder
    )

  protected[cassandra] def registersInsertBinder: (BestBlockInserted, PreparedStatement) => ArraySeq[BoundStatement] = {
    case (BestBlockInserted(_, Some(block)), statement) =>
      block.registers.map { r =>
        // format: off
        statement
          .bind()
          .setString(header_id,             block.header.id)
          .setString(id,                    r.id.toString)
          .setString(box_id,                r.boxId.unwrapped)
          .setString(value_type,            r.sigmaType.toString)
          .setString(serialized_value,      r.renderedValue)
        // format: on
      }
    case _ =>
      throw new IllegalStateException("Backend must be enabled")
  }

}

object Registers {
  protected[cassandra] val node_registers_table = "node_registers"

  protected[cassandra] val header_id        = "header_id"
  protected[cassandra] val id               = "id"
  protected[cassandra] val box_id           = "box_id"
  protected[cassandra] val value_type       = "value_type"
  protected[cassandra] val serialized_value = "serialized_value"

  protected[cassandra] val columns = Seq(
    header_id,
    id,
    box_id,
    value_type,
    serialized_value
  )
}
