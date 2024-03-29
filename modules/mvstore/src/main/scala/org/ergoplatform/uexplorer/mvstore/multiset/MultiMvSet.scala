package org.ergoplatform.uexplorer.mvstore.multiset

import org.ergoplatform.uexplorer.mvstore.*
import org.ergoplatform.uexplorer.mvstore.SuperNodeCounter.HotKey
import org.h2.mvstore.{MVMap, MVStore}
import zio.{Task, ZIO}

import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

case class MultiMvSet[K, C[A] <: java.util.Collection[A], V](
  commonMap: MapLike[K, C[V]],
  superNodeMap: SuperNodeMvSet[K, C, V]
)(implicit c: MultiSetCodec[C, V])
  extends MultiSetLike[K, C, V] {

  def isEmpty: Boolean = superNodeMap.isEmpty && commonMap.isEmpty

  def get(k: K): Option[C[V]] =
    superNodeMap
      .get(k)
      .orElse(commonMap.get(k))

  def contains(k: K): Boolean = superNodeMap.contains(k) || commonMap.containsKey(k)

  def multiSize: MultiColSize = MultiColSize(superNodeMap.size, superNodeMap.totalSize, commonMap.size)

  def clearEmptySuperNodes: Task[Unit] =
    superNodeMap.clearEmptyOrClosedSuperNodes()

  def getReport: (Path, Vector[HotKey]) =
    superNodeMap.getReport

  def removeSubsetOrFail(k: K, values: IterableOnce[V], size: Int)(f: C[V] => Option[C[V]]): Try[Unit] =
    superNodeMap.removeAllOrFail(k, values, size).fold(commonMap.removeOrUpdateOrFail(k)(f))(identity)

  def adjustAndForget(k: K, values: IterableOnce[V], size: Int): Try[_] =
    superNodeMap.putAllNewOrFail(k, values, size).getOrElse {
      val (appended, _) = commonMap.adjustCollection(k)(c.append(values))
      if (appended)
        Success(())
      else
        Failure(new AssertionError(s"All inserted values under key $k should be appended"))
    }

}

object MultiMvSet {
  def apply[K, C[A] <: java.util.Collection[A], V](
    id: MultiColId,
    hotKeyDir: Path
  )(implicit
    store: MVStore,
    c: MultiSetCodec[C, V],
    sc: SuperNodeSetCodec[C, V],
    vc: ValueCodec[SuperNodeCounter],
    kc: HotKeyCodec[K]
  ): Task[MultiMvSet[K, C, V]] =
    for
      superSet  <- SuperNodeMvSet[K, C, V](id, hotKeyDir)
      commonMap <- superSet.mergeCommonMap
      _         <- ZIO.attempt(store.commit())
    yield MultiMvSet(commonMap, superSet)

}
