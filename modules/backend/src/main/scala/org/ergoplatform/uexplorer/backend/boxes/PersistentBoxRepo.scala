package org.ergoplatform.uexplorer.backend.boxes

import io.getquill.*
import org.ergoplatform.uexplorer.{BoxId, ErgoTreeHash, ErgoTreeT8Hash}
import org.ergoplatform.uexplorer.backend.Codecs
import org.ergoplatform.uexplorer.db.*
import zio.*

import java.sql.SQLException
import javax.sql.DataSource

case class PersistentBoxRepo(ds: DataSource) extends BoxRepo with Codecs:
  val ctx = new H2ZioJdbcContext(Literal)
  import ctx.*

  private val dsLayer = ZLayer.succeed(ds)

  private def insertUtxosQuery(utxos: Iterable[Utxo]) =
    quote {
      liftQuery(utxos).foreach(utxo => query[Utxo].insertValue(utxo))
    }
  private def insertBoxesQuery(boxes: Iterable[Box]) =
    quote {
      liftQuery(boxes).foreach(box => query[Box].insertValue(box))
    }
  private def insertErgoTreesQuery(ergoTrees: Iterable[ErgoTree]) =
    quote { // todo https://github.com/zio/zio-protoquill/issues/298
      liftQuery(ergoTrees).foreach(ergoTree => query[ErgoTree].insertValue(ergoTree).onConflictIgnore)
    }
  private def insertErgoTreeT8sQuery(ergoTreeT8s: Iterable[ErgoTreeT8]) =
    quote { // todo https://github.com/zio/zio-protoquill/issues/298
      liftQuery(ergoTreeT8s).foreach(ergoTreeT8 => query[ErgoTreeT8].insertValue(ergoTreeT8).onConflictIgnore)
    }
  private def insertErgoTreeQuery(ergoTree: ErgoTree) =
    quote {
      query[ErgoTree].insertValue(lift(ergoTree)).onConflictIgnore
    }
  private def insertErgoTreeT8Query(ergoTreeT8: ErgoTreeT8) =
    quote {
      query[ErgoTreeT8].insertValue(lift(ergoTreeT8)).onConflictIgnore
    }

  override def insertUtxos(
    ergoTrees: Iterable[ErgoTree],
    ergoTreeT8s: Iterable[ErgoTreeT8],
    utxos: Iterable[Utxo]
  ): Task[Iterable[BoxId]] =
    (for {
      _ <- ZIO.foreachDiscard(ergoTrees)(et => ctx.run(insertErgoTreeQuery(et))) // todo 298
      _ <- ZIO.foreachDiscard(ergoTreeT8s)(etT8 => ctx.run(insertErgoTreeT8Query(etT8)))
      _ <- ctx.run(insertBoxesQuery(utxos.map(_.toBox)))
      _ <- ctx.run(insertUtxosQuery(utxos))
    } yield utxos.map(_.boxId)).provide(dsLayer)

  override def deleteUtxo(boxId: BoxId): Task[Long] =
    ctx
      .run {
        quote {
          query[Utxo].filter(p => p.boxId == lift(boxId)).delete
        }
      }
      .provide(dsLayer)

  override def deleteUtxos(boxIds: Iterable[BoxId]): Task[Long] =
    ctx
      .run {
        quote {
          query[Utxo].filter(p => liftQuery(boxIds).contains(p.boxId)).delete
        }
      }
      .provide(dsLayer)

  override def lookupBox(boxId: BoxId): Task[Option[Box]] =
    ctx
      .run {
        quote {
          query[Box]
            .filter(p => p.boxId == lift(boxId))
        }
      }
      .provide(dsLayer)
      .map(_.headOption)

  override def lookupUtxo(boxId: BoxId): Task[Option[Utxo]] =
    ctx
      .run {
        quote {
          query[Utxo]
            .filter(p => p.boxId == lift(boxId))
        }
      }
      .provide(dsLayer)
      .map(_.headOption)

  override def lookupBoxes(ids: Set[BoxId]): Task[List[Box]] =
    ctx
      .run {
        quote {
          query[Box]
            .filter(p => liftQuery(ids).contains(p.boxId))
        }
      }
      .provide(dsLayer)

  override def lookupUtxos(ids: Set[BoxId]): Task[List[Utxo]] =
    ctx
      .run {
        quote {
          query[Utxo]
            .filter(p => liftQuery(ids).contains(p.boxId))
        }
      }
      .provide(dsLayer)

  override def lookupBoxesByHash(etHash: ErgoTreeHash, columns: List[String], filter: Map[String, Any]): Task[Iterable[Box]] =
    ctx
      .run {
        quote {
          query[ErgoTree]
            .join(query[Box])
            .on((et, box) => et.hash == box.ergoTreeHash)
            .filter((et, _) => et.hash == lift(etHash))
            .map((_, b) => Box(b.boxId, b.blockId, b.txId, b.ergoTreeHash, b.ergoTreeT8Hash, b.ergValue, b.r4, b.r5, b.r6, b.r7, b.r8, b.r9))
            .filterByKeys(filter)
            .filterColumns(columns)
        }
      }
      .provide(dsLayer)

  override def lookupUtxosByHash(etHash: ErgoTreeHash, columns: List[String], filter: Map[String, Any]): Task[Iterable[Utxo]] =
    ctx
      .run {
        quote {
          query[ErgoTree]
            .join(query[Utxo])
            .on((et, utxo) => et.hash == utxo.ergoTreeHash)
            .filter((et, _) => et.hash == lift(etHash))
            .map((_, b) => Utxo(b.boxId, b.blockId, b.txId, b.ergoTreeHash, b.ergoTreeT8Hash, b.ergValue, b.r4, b.r5, b.r6, b.r7, b.r8, b.r9))
            .filterByKeys(filter)
            .filterColumns(columns)
        }
      }
      .provide(dsLayer)

  override def lookupUtxoIdsByHash(etHash: ErgoTreeHash): Task[Set[BoxId]] =
    ctx
      .run {
        quote {
          query[Utxo]
            .filter(_.ergoTreeHash == lift(etHash))
            .map(_.boxId)
        }
      }
      .map(_.toSet)
      .provide(dsLayer)

  override def lookupBoxesByT8Hash(etT8Hash: ErgoTreeT8Hash, columns: List[String], filter: Map[String, Any]): Task[Iterable[Box]] =
    ctx
      .run {
        quote {
          query[ErgoTreeT8]
            .join(query[Box])
            .on((et, box) => box.ergoTreeT8Hash.contains(et.hash))
            .filter((et, _) => et.hash == lift(etT8Hash))
            .map((_, b) => Box(b.boxId, b.blockId, b.txId, b.ergoTreeHash, b.ergoTreeT8Hash, b.ergValue, b.r4, b.r5, b.r6, b.r7, b.r8, b.r9))
            .filterByKeys(filter)
            .filterColumns(columns)
        }
      }
      .provide(dsLayer)

  override def lookupUtxosByT8Hash(etT8Hash: ErgoTreeT8Hash, columns: List[String], filter: Map[String, Any]): Task[Iterable[Utxo]] =
    ctx
      .run {
        quote {
          query[ErgoTreeT8]
            .join(query[Utxo])
            .on((et, box) => box.ergoTreeT8Hash.contains(et.hash))
            .filter((et, _) => et.hash == lift(etT8Hash))
            .map((_, b) => Utxo(b.boxId, b.blockId, b.txId, b.ergoTreeHash, b.ergoTreeT8Hash, b.ergValue, b.r4, b.r5, b.r6, b.r7, b.r8, b.r9))
            .filterByKeys(filter)
            .filterColumns(columns)
        }
      }
      .provide(dsLayer)

  override def lookupUtxoIdsByT8Hash(etT8Hash: ErgoTreeT8Hash): Task[Set[BoxId]] =
    ctx
      .run {
        quote {
          query[Utxo]
            .filter(_.ergoTreeT8Hash.contains(lift(etT8Hash)))
            .map(_.boxId)
        }
      }
      .map(_.toSet)
      .provide(dsLayer)

  override def isEmpty: Task[Boolean] =
    ctx
      .run {
        quote {
          query[Utxo].take(1).isEmpty
        }
      }
      .provide(dsLayer)

object PersistentBoxRepo:
  def layer: ZLayer[DataSource, Nothing, PersistentBoxRepo] =
    ZLayer.fromFunction(PersistentBoxRepo(_))
