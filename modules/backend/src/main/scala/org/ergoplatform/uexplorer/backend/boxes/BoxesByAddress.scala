package org.ergoplatform.uexplorer.backend.boxes

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.uexplorer.backend.{Codecs, ErrorResponse, TapirRoutes}
import org.ergoplatform.uexplorer.db.{Box, BoxWithAssets, Utxo}
import org.ergoplatform.uexplorer.{BlockId, BoxId, TxId}
import sttp.model.{QueryParams, StatusCode}
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import sttp.tapir.ztapir.*
import sttp.tapir.{queryParams, PublicEndpoint, Schema}
import zio.*
import zio.json.*

trait BoxesByAddress extends TapirRoutes with Codecs:

  protected[backend] val spentBoxesByAddress: PublicEndpoint[(String, QueryParams), (ErrorResponse, StatusCode), Iterable[BoxWithAssets], Any] =
    endpoint.get
      .in(rootPath / "boxes" / "spent" / "by-address" / path[String]("address"))
      .in(queryParams)
      .errorOut(jsonBody[ErrorResponse])
      .errorOut(statusCode)
      .out(jsonBody[Iterable[BoxWithAssets]])
      .description("Get spent boxes by address (base58)")

  protected[backend] def spentBoxesByAddressEndpoint(implicit enc: ErgoAddressEncoder): ZServerEndpoint[BoxService, Any] =
    spentBoxesByAddress.zServerLogic { case (address, qp) =>
      BoxService
        .getSpentBoxesByAddress(address, qp.toMap)
        .mapError(handleThrowable)
    }

  protected[backend] val spentBoxIdsByAddress: PublicEndpoint[(String, QueryParams), (ErrorResponse, StatusCode), Iterable[BoxId], Any] =
    endpoint.get
      .in(rootPath / "box-ids" / "spent" / "by-address" / path[String]("address"))
      .in(queryParams)
      .errorOut(jsonBody[ErrorResponse])
      .errorOut(statusCode)
      .out(jsonBody[Iterable[BoxId]])
      .description("Get spent box IDs by address (base58)")

  protected[backend] def spentBoxIdsByAddressEndpoint: ZServerEndpoint[BoxService, Any] =
    spentBoxIdsByAddress.zServerLogic { case (address, qp) =>
      BoxService
        .getSpentBoxIdsByAddress(address, qp.toMap)
        .mapError(handleThrowable)
    }

  protected[backend] val unspentBoxesByAddress: PublicEndpoint[(String, QueryParams), (ErrorResponse, StatusCode), Iterable[BoxWithAssets], Any] =
    endpoint.get
      .in(rootPath / "boxes" / "unspent" / "by-address" / path[String]("address"))
      .in(queryParams)
      .errorOut(jsonBody[ErrorResponse])
      .errorOut(statusCode)
      .out(jsonBody[Iterable[BoxWithAssets]])
      .description("Get only unspent boxes by address (base58)")

  protected[backend] def unspentBoxesByAddressEndpoint(implicit enc: ErgoAddressEncoder): ZServerEndpoint[BoxService, Any] =
    unspentBoxesByAddress.zServerLogic { case (address, qp) =>
      BoxService
        .getUnspentBoxesByAddress(address, qp.toMap)
        .mapError(handleThrowable)
    }

  protected[backend] val unspentBoxIdsByAddress: PublicEndpoint[(String, QueryParams), (ErrorResponse, StatusCode), Iterable[BoxId], Any] =
    endpoint.get
      .in(rootPath / "box-ids" / "unspent" / "by-address" / path[String]("address"))
      .in(queryParams)
      .errorOut(jsonBody[ErrorResponse])
      .errorOut(statusCode)
      .out(jsonBody[Iterable[BoxId]])
      .description("Get only unspent box IDs by address (base58)")

  protected[backend] val unspentBoxIdsByAddressEndpoint: ZServerEndpoint[BoxService, Any] =
    unspentBoxIdsByAddress.zServerLogic { case (address, qp) =>
      BoxService
        .getUnspentBoxIdsByAddress(address, qp.toMap)
        .mapError(handleThrowable)
    }

  protected[backend] val anyBoxesByAddress: PublicEndpoint[(String, QueryParams), (ErrorResponse, StatusCode), Iterable[BoxWithAssets], Any] =
    endpoint.get
      .in(rootPath / "boxes" / "any" / "by-address" / path[String]("address"))
      .in(queryParams)
      .errorOut(jsonBody[ErrorResponse])
      .errorOut(statusCode)
      .out(jsonBody[Iterable[BoxWithAssets]])
      .description("Get any (spent or unspent) boxes by address (base58)")

  protected[backend] def anyBoxesByAddressEndpoint(implicit enc: ErgoAddressEncoder): ZServerEndpoint[BoxService, Any] =
    anyBoxesByAddress.zServerLogic { case (address, qp) =>
      BoxService
        .getAnyBoxesByAddress(address, qp.toMap)
        .mapError(handleThrowable)
    }

  protected[backend] val anyBoxIdsByAddress: PublicEndpoint[(String, QueryParams), (ErrorResponse, StatusCode), Iterable[BoxId], Any] =
    endpoint.get
      .in(rootPath / "box-ids" / "any" / "by-address" / path[String]("address"))
      .in(queryParams)
      .errorOut(jsonBody[ErrorResponse])
      .errorOut(statusCode)
      .out(jsonBody[Iterable[BoxId]])
      .description("Get any (spent or unspent) box IDs by address (base58)")

  protected[backend] val anyBoxIdsByAddressEndpoint: ZServerEndpoint[BoxService, Any] =
    anyBoxIdsByAddress.zServerLogic { case (address, qp) =>
      BoxService
        .getAnyBoxIdsByAddress(address, qp.toMap)
        .mapError(handleThrowable)
    }
