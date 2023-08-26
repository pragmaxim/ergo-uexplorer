package org.ergoplatform.uexplorer.backend.blocks

import org.ergoplatform.uexplorer.{Address, BlockId}
import org.ergoplatform.uexplorer.db.Block
import zio.{ExitCode, RIO, Task, URIO, ZIO, ZIOAppDefault, ZLayer}
import sttp.tapir.{PublicEndpoint, Schema}
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*
import sttp.tapir.json.zio.*
import zio.http.HttpApp
import zio.http.Server
import org.ergoplatform.uexplorer.BlockId.unwrapped
import org.ergoplatform.uexplorer.backend.Codecs
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint
import zio.json.{JsonDecoder, JsonEncoder}

object BlockTapirRoutes extends Codecs:

  val infoEndpoint: PublicEndpoint[Unit, String, Info, Any] =
    endpoint.get
      .in("info")
      .errorOut(stringBody)
      .out(jsonBody[Info])

  val infoServerEndpoint: ZServerEndpoint[BlockRepo, Any] =
    infoEndpoint.zServerLogic { _ =>
      BlockRepo
        .getLastBlocks(1)
        .map(_.headOption)
        .mapBoth(
          _.getMessage,
          {
            case Some(lastBlock) =>
              Info(lastBlock.height)
            case None =>
              Info(0)
          }
        )
    }

  val blockByIdEndpoint: PublicEndpoint[BlockId, String, Block, Any] =
    endpoint.get
      .in("blocks" / path[String]("blockId"))
      .mapIn(BlockId.fromStringUnsafe)(_.unwrapped)
      .errorOut(stringBody)
      .out(jsonBody[Block])

  val blockByIdServerEndpoint: ZServerEndpoint[BlockRepo, Any] =
    blockByIdEndpoint.zServerLogic { blockId =>
      BlockRepo
        .lookup(blockId)
        .mapError(_.getMessage)
        .flatMap {
          case Some(block) =>
            ZIO.succeed(block)
          case None =>
            ZIO.fail("404 Not Found")
        }
    }

  val blockByIdsEndpoint: PublicEndpoint[Set[BlockId], String, List[Block], Any] =
    endpoint.post
      .in("blocks")
      .in(jsonBody[Set[BlockId]])
      .errorOut(stringBody)
      .out(jsonBody[List[Block]])

  val blockByIdsServerEndpoint: ZServerEndpoint[BlockRepo, Any] =
    blockByIdsEndpoint.zServerLogic { blockIds =>
      BlockRepo
        .lookupBlocks(blockIds)
        .mapError(_.getMessage)
    }

  val swaggerEndpoints: Seq[ServerEndpoint[Any, RIO[BlockRepo, *]]] =
    SwaggerInterpreter().fromEndpoints[RIO[BlockRepo, *]](List(infoEndpoint, blockByIdEndpoint, blockByIdsEndpoint), "Block api", "1.0")

  val routes: HttpApp[BlockRepo, Throwable] =
    ZioHttpInterpreter()
      .toHttp[BlockRepo](List(infoServerEndpoint, blockByIdServerEndpoint, blockByIdsServerEndpoint) ++ swaggerEndpoints)