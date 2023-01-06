package org.ergoplatform.uexplorer.indexer.cassandra.entity

import akka.NotUsed
import akka.stream.ActorAttributes
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.cql.{BoundStatement, DefaultBatchType, PreparedStatement, SimpleStatement}
import com.datastax.oss.driver.api.core.data.TupleValue
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, insertInto}
import com.typesafe.scalalogging.LazyLogging
import eu.timepit.refined.auto.*
import org.apache.commons.codec.digest.MurmurHash2
import org.apache.tinkerpop.gremlin.structure.{Graph, T, Vertex}
import org.ergoplatform.uexplorer.db.Block
import org.ergoplatform.uexplorer.indexer.cassandra.{CassandraBackend, EpochPersistenceSupport}
import org.ergoplatform.uexplorer.indexer.chain.ChainStateHolder.*
import org.ergoplatform.uexplorer.indexer.chain.{Epoch, InvalidEpochCandidate}
import org.ergoplatform.uexplorer.indexer.{AkkaStreamSupport, Utils}
import org.ergoplatform.uexplorer.{Address, BoxId, Const, TxId}
import org.janusgraph.core.{JanusGraphVertex, VertexLabel}

import scala.collection.immutable.{ArraySeq, TreeMap}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

trait CassandraEpochWriter extends AkkaStreamSupport with LazyLogging {
  this: CassandraBackend =>
  import CassandraEpochWriter.*

  def epochsWriteFlow: Flow[(Block, Option[MaybeNewEpoch]), (Block, Option[MaybeNewEpoch]), NotUsed] =
    storeBatchFlow(
      parallelism = 1,
      batchType   = DefaultBatchType.LOGGED,
      buildInsertStatement(List(epoch_index, last_header_id), node_epoch_last_headers_table),
      epochLastHeadersInsertBinder
    )

}

object CassandraEpochWriter extends EpochPersistenceSupport with LazyLogging {

  protected[cassandra] def epochLastHeadersInsertBinder
    : ((Block, Option[MaybeNewEpoch]), PreparedStatement) => ArraySeq[BoundStatement] = {
    case ((_, Some(NewEpochDetected(epoch, _))), stmt) =>
      ArraySeq(
        stmt
          .bind()
          .setInt(epoch_index, epoch.index)
          .setString(last_header_id, epoch.blockIds.last)
      )
    case ((_, Some(NewEpochExisted(epochIndex))), _) =>
      logger.debug(s"Skipping persistence of epoch $epochIndex as it already existed")
      ArraySeq.empty
    case _ =>
      ArraySeq.empty
  }

}
