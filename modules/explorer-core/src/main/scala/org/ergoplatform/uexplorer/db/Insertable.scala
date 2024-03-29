package org.ergoplatform.uexplorer.db

import org.ergoplatform.uexplorer.BlockId
import org.ergoplatform.uexplorer.db.ForkInserted.{LoosingFork, WinningFork}

sealed trait Insertable

case class BestBlockInserted(linkedBlock: LinkedBlock, fullBlockOpt: Option[FullBlock]) extends Insertable

case class ForkInserted(winningFork: WinningFork, loosingFork: LoosingFork) extends Insertable

object ForkInserted {
  type WinningFork = List[LinkedBlock]
  type LoosingFork = List[(BlockId, Block)]
}
