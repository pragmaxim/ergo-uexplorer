package org.ergoplatform.uexplorer.db

import org.ergoplatform.uexplorer.BlockId
import zio.Task

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Backend {

  def isEmpty: Task[Boolean]

  def removeBlocks(blockIds: Set[BlockId]): Task[Unit]

  def writeBlock(b: NormalizedBlock, condition: Task[Any]): Task[BlockId]

  def close(): Task[Unit]
}
