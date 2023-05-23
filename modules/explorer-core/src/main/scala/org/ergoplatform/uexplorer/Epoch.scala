package org.ergoplatform.uexplorer

import org.ergoplatform.uexplorer.{Address, BlockId, BoxId, Const, EpochIndex, Height}

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import org.ergoplatform.uexplorer.UnexpectedStateError
import scala.collection.immutable.TreeMap

case class Epoch(
  index: Int,
  blockIds: Vector[BlockId]
)

object Epoch {

  private val FlushHeight = 32

  sealed trait EpochCommand
  case class WriteNewEpoch(
    epoch: Epoch,
    txBoxesByHeight: TreeMap[Height, BoxesByTx],
    topAddresses: TopAddressMap
  ) extends EpochCommand {

    override def toString: String =
      s"New epoch ${epoch.index} to be written"
  }

  case class IgnoreEpoch(epochIndex: Int) extends EpochCommand {

    override def toString: String =
      s"Epoch $epochIndex ignored"
  }

  def epochIndexForHeight(height: Int): Int = {
    if (height < 1) throw new UnexpectedStateError("Height must start from 1 as genesis block is not part of an epoch")
    (height - 1) / Const.EpochLength
  }

  def heightRangeForEpochIndex(index: EpochIndex): Seq[Height] = {
    if (index < 0) throw new UnexpectedStateError("Negative epoch index is illegal")
    val epochStartHeight = index * Const.EpochLength + 1
    val epochEndHeight   = epochStartHeight + 1023
    epochStartHeight to epochEndHeight
  }

  def heightAtFlushPoint(height: Int): Boolean =
    Epoch.epochIndexForHeight(height) > 0 && height % Const.EpochLength == FlushHeight

}