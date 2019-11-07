package com.horizen.api.http

import java.net.{InetAddress, InetSocketAddress, URI}
import java.nio.ByteBuffer

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.http.SidechainNodeRestSchema._
import scorex.core.settings.RESTApiSettings

import scala.concurrent.{Await, ExecutionContext}
import scorex.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import scorex.core.network.peer.PeerInfo
import scorex.core.network.peer.PeerManager.ReceivableMessages.{GetAllPeers, GetBlacklistedPeers}
import scorex.core.utils.NetworkTimeProvider
import JacksonSupport._
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.WebSocketSettings
import com.horizen.api.http.SidechainNodeErrorResponse.ErrorInvalidHost
import com.horizen.serialization.Views
import javax.websocket.{Endpoint, EndpointConfig, Session}
import org.glassfish.tyrus.client.ClientManager

case class SidechainNodeApiRoute(peerManager: ActorRef,
                                 networkController: ActorRef,
                                 timeProvider: NetworkTimeProvider,
                                 websocketSettings: WebSocketSettings,
                                 override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute {

  override val route: Route = (pathPrefix("node")) {
    connect ~ allPeers ~ connectedPeers ~ blacklistedPeers ~ websocketStatus
  }

  private val addressAndPortRegexp = "([\\w\\.]+):(\\d{1,5})".r

  def allPeers: Route = (path("allPeers") & post) {
    try {
      val result = askActor[Map[InetSocketAddress, PeerInfo]](peerManager, GetAllPeers).map {
        _.map { case (address, peerInfo) =>
          SidechainPeerNode(address.toString, peerInfo.lastSeen, peerInfo.peerSpec.nodeName, peerInfo.connectionType.map(_.toString))
        }
      }
      val resultList = Await.result(result, settings.timeout).toList
      ApiResponseUtil.toResponse(RespAllPeers(resultList))
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

  def connectedPeers: Route = (path("connectedPeers") & post) {
    try {
      val result = askActor[Seq[PeerInfo]](networkController, GetConnectedPeers).map {
        _.map { peerInfo =>
          SidechainPeerNode(
            address = peerInfo.peerSpec.address.map(_.toString).getOrElse(""),
            lastSeen = peerInfo.lastSeen,
            name = peerInfo.peerSpec.nodeName,
            connectionType = peerInfo.connectionType.map(_.toString)
          )
        }
      }
      val resultList = Await.result(result, settings.timeout).toList
      ApiResponseUtil.toResponse(RespAllPeers(resultList))
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

  def connect: Route = (post & path("connect")) {
    entity(as[ReqConnect]) {
      body =>
        var address = body.host + ":" + body.port
        val maybeAddress = addressAndPortRegexp.findFirstMatchIn(address)
        maybeAddress match {
          case None =>
            ApiResponseUtil.toResponse(ErrorInvalidHost("Incorrect host and/or port.", None))
          case Some(addressAndPort) =>
            val host = InetAddress.getByName(addressAndPort.group(1))
            val port = addressAndPort.group(2).toInt
            networkController ! ConnectTo(PeerInfo.fromAddress(new InetSocketAddress(host, port)))
            ApiResponseUtil.toResponse(RespConnect(host + ":" + port))
        }
    }
  }

  def blacklistedPeers: Route = (path("blacklistedPeers") & post) {
    try {
      val result = askActor[Seq[InetAddress]](peerManager, GetBlacklistedPeers)
        .map(x => RespBlacklistedPeers(x.map(_.toString)))
      val resultList = Await.result(result, settings.timeout)
      ApiResponseUtil.toResponse(resultList)
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

  def websocketStatus: Route = (path("websocketStatus") & post) {
    val address = websocketSettings.address
    try {
      val client = ClientManager.createClient()
      val userSession = client.connectToServer(new Endpoint {
        override def onOpen(session: Session, endpointConfig: EndpointConfig): Unit = {

        }
      }, new URI(address))
      userSession.getBasicRemote.sendPing(ByteBuffer.wrap("Ping".getBytes()))
      ApiResponseUtil.toResponse(RespWebsocketStaus(WebsocketStatus("Connected", address)))
    } catch {
      case e: Throwable => ApiResponseUtil.toResponse(RespWebsocketStaus(WebsocketStatus("Disconnected", address)))
    }
  }

}

object SidechainNodeRestSchema {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllPeers(peers: List[SidechainPeerNode]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class SidechainPeerNode(address: String, lastSeen: Long, name: String, connectionType: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespBlacklistedPeers(addresses: Seq[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqConnect(host: String, port: Int)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespConnect(connectedTo: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespWebsocketStaus(websocket: WebsocketStatus) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class WebsocketStatus(status: String, serverAddress: String)

}

object SidechainNodeErrorResponse {

  case class ErrorInvalidHost(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0401"
  }

}