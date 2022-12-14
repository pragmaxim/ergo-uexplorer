package org.ergoplatform.uexplorer.indexer

import java.io.BufferedInputStream
import java.util.zip.GZIPInputStream
import scala.collection.immutable.{SortedMap, TreeMap}
import scala.io.Source

object Rest {

  private def loadCacheFromFile(fileName: String) =
    Source
      .fromInputStream(
        new GZIPInputStream(
          new BufferedInputStream(Thread.currentThread().getContextClassLoader.getResourceAsStream(fileName))
        )
      )
      .getLines()
      .filterNot(_.trim.isEmpty)
      .foldLeft(1 -> TreeMap.empty[Int, String]) { case ((height, cache), blockStr) =>
        height + 1 -> cache.updated(height, blockStr)
      }
      ._2

  object info {
    val minNodeHeight            = 4000
    val sync                     = "sync.json"
    val poll                     = "poll.json"
    val woRestApiAndFullHeight   = "wo-rest-api-and-full-height.json"
    val woRestApiAndWoFullHeight = "wo-rest-api-and-wo-full-height.json"
  }

  object blockIds {
    lazy val byHeight: SortedMap[Int, String] = loadCacheFromFile("blocks/block_ids.gz")
  }

  object blocks {
    lazy val byHeight: SortedMap[Int, String] = loadCacheFromFile("blocks/blocks.gz")

    lazy val byId: SortedMap[String, String] = byHeight.map { case (height, block) =>
      if (height == 4201)
        println(block)
      blockIds.byHeight(height) -> block
    }
  }
}
