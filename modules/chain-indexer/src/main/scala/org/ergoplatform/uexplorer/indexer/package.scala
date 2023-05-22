package org.ergoplatform.uexplorer

import org.apache.commons.codec.digest.MurmurHash2
import org.janusgraph.graphdb.database.StandardJanusGraph
import org.janusgraph.graphdb.transaction.StandardJanusGraphTx
import sttp.model.Uri
import sttp.model.Uri.{EmptyPath, QuerySegment}

import scala.collection.mutable

package object indexer {

  implicit class MapPimp[K, V](underlying: Map[K, V]) {

    def putOrRemove(k: K)(f: Option[V] => Option[V]): Map[K, V] =
      f(underlying.get(k)) match {
        case None    => underlying removed k
        case Some(v) => underlying updated (k, v)
      }

    def adjust(k: K)(f: Option[V] => V): Map[K, V] = underlying.updated(k, f(underlying.get(k)))
  }

  implicit class MutableMapPimp[K, V](underlying: mutable.Map[K, V]) {

    def putOrRemove(k: K)(f: Option[V] => Option[V]): mutable.Map[K, V] =
      f(underlying.get(k)) match {
        case None => underlying -= k
        case Some(v) =>
          underlying.put(k, v)
          underlying
      }

    def adjust(k: K)(f: Option[V] => V): mutable.Map[K, V] = {
      underlying.put(k, f(underlying.get(k)))
      underlying
    }
  }

  object Utils {

    def vertexHash(address: String, g: StandardJanusGraph): Long =
      g.getIDManager.toVertexId(Math.abs(MurmurHash2.hash64(address)) / 1000)

    def vertexHash(address: String)(implicit tx: StandardJanusGraphTx): Long =
      vertexHash(address, tx.getGraph)
  }
}
