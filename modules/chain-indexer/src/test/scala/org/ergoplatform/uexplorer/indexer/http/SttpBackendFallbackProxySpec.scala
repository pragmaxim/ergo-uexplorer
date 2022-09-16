package org.ergoplatform.uexplorer.indexer.http

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import org.ergoplatform.uexplorer.indexer.http.NodePool.{GetAvailablePeers, NodePoolState}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.collection.immutable.TreeSet
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

class SttpBackendFallbackProxySpec extends AsyncFreeSpec with Matchers with BeforeAndAfterAll with ScalaFutures {

  private val appVersion = "4.0.42"
  private val stateType  = "utxo"

  private val localNode  = LocalNode(uri"http://localNode", appVersion, stateType, 0)
  private val remoteNode = RemoteNode(uri"http://remoteNode", appVersion, stateType, 0)
  private val remotePeer = RemotePeer(uri"http://remotePeer", appVersion, stateType, 0)

  private val proxyUri = uri"http://proxy"

  private val testKit                      = ActorTestKit()
  implicit private val sys: ActorSystem[_] = testKit.internalSystem
  implicit val timeout: Timeout            = 3.seconds

  override def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
  }

  "fallback proxy should try all peers in given order until one succeeds" in {
    implicit val testingBackend: SttpBackendStub[Future, _] = SttpBackendStub.asynchronousFuture.whenAnyRequest
      .thenRespondCyclicResponses(
        Response("error", StatusCode.InternalServerError, "Local node failed"),
        Response("error", StatusCode.InternalServerError, "Remote node not available"),
        Response.ok[String]("Remote peer available")
      )

    def proxyRequest(p: Peer) =
      basicRequest.get(p.uri).responseGetRight.send(testingBackend).map(_.body)

    SttpBackendFallbackProxy
      .fallbackQuery[String](List(localNode, remoteNode, remotePeer), TreeSet.empty)(proxyRequest)
      .map { case (invalidPeers, response) =>
        response shouldBe Success("Remote peer available")
        invalidPeers shouldBe TreeSet(localNode, remoteNode)
      }
  }

  "fallback proxy should swap uri in request" in {
    SttpBackendFallbackProxy.swapUri(basicRequest.get(proxyUri), localNode.uri) shouldBe basicRequest.get(localNode.uri)
  }

  "fallback proxy should get peers from node pool to proxy request to and invalidate failing peers" in {
    implicit val underlyingBackend: SttpBackendStub[Future, capabilities.WebSockets] = SttpBackendStub.asynchronousFuture
      .whenRequestMatches(_.uri == localNode.uri)
      .thenRespond(Response("error", StatusCode.InternalServerError, "Local node failed"))
      .whenRequestMatches(_.uri == remoteNode.uri)
      .thenRespond("Remote peer available")
      .whenRequestMatches(_.uri == proxyUri)
      .thenRespondServerError()

    val nodePoolRef =
      testKit.spawn(NodePool.initialized(NodePoolState(TreeSet(localNode, remoteNode, remotePeer), TreeSet.empty)))
    val fallbackProxyBackend = new SttpBackendFallbackProxy[capabilities.WebSockets](nodePoolRef)

    fallbackProxyBackend
      .send(basicRequest.get(proxyUri).responseGetRight)
      .map(_.body)
      .map { resp =>
        resp shouldBe "Remote peer available"
      }
      .flatMap { _ =>
        nodePoolRef.ask(ref => GetAvailablePeers(ref)).map { availablePeers =>
          availablePeers.peerAddresses shouldBe List(remoteNode, remotePeer)
        }
      }
  }

}
