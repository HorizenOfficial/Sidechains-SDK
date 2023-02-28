package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.AbstractSidechainApp
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetStorageVersions
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.route.SidechainNodeErrorResponse.{ErrorInvalidHost, ErrorStopNodeAlreadyInProgress}
import io.horizen.api.http.route.SidechainNodeRestSchema._
import io.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiError, SuccessResponse}
import io.horizen.json.Views
import io.horizen.params.NetworkParams
import io.horizen.utils.BytesUtils
import sparkz.core.api.http.ApiRoute
import sparkz.core.network.ConnectedPeer
import sparkz.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import sparkz.core.network.peer.PeerInfo
import sparkz.core.network.peer.PeerManager.ReceivableMessages.{DisconnectFromAddress, GetAllPeers, GetBlacklistedPeers, RemovePeer}
import sparkz.core.settings.RESTApiSettings
import sparkz.core.utils.NetworkTimeProvider

import java.lang.Thread.sleep
import java.net.{InetAddress, InetSocketAddress}
import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

case class SidechainNodeApiRoute(peerManager: ActorRef,
                                 networkController: ActorRef,
                                 timeProvider: NetworkTimeProvider,
                                 override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, app: AbstractSidechainApp, params: NetworkParams)
                                (implicit val context: ActorRefFactory, val ec: ExecutionContext) extends ApiRoute {


  override val route: Route = pathPrefix("node") {

    connect ~ allPeers ~ connectedPeers ~ blacklistedPeers ~ disconnect ~ stop ~ getNodeStorageVersions ~ getSidechainId
  }

  private val addressAndPortRegexp = "([\\w\\.]+):(\\d{1,5})".r

  def allPeers: Route = (path("allPeers") & post) {
    withBasicAuth {
      _ => {
        try {
          val result = askActor[Map[InetSocketAddress, PeerInfo]](peerManager, GetAllPeers).map {
            _.map { case (address, peerInfo) =>
              SidechainPeerNode(address.toString, None, peerInfo.lastHandshake, 0, peerInfo.peerSpec.nodeName, peerInfo.peerSpec.agentName, peerInfo.peerSpec.protocolVersion.toString, peerInfo.connectionType.map(_.toString))
            }
          }
          val resultList = Await.result(result, settings.timeout).toList
          ApiResponseUtil.toResponse(RespAllPeers(resultList))
        } catch {
          case e: Throwable => SidechainApiError(e)
        }
      }
    }
  }

  def connectedPeers: Route = (path("connectedPeers") & post) {
    withBasicAuth {
      _ => {
        try {
          val result = askActor[Seq[ConnectedPeer]](networkController, GetConnectedPeers).map {
            _.flatMap { connection =>
              connection.peerInfo.map {
                peerInfo =>
                  SidechainPeerNode(
                    remoteAddress = connection.connectionId.remoteAddress.toString,
                    localAddress = Some(connection.connectionId.localAddress.toString),
                    lastHandshake = peerInfo.lastHandshake,
                    lastMessage = connection.lastMessage,
                    name = peerInfo.peerSpec.nodeName,
                    agentName = peerInfo.peerSpec.agentName,
                    protocolVersion = peerInfo.peerSpec.protocolVersion.toString,
                    connectionType = peerInfo.connectionType.map(_.toString)
                  )
              }
            }
          }
          val resultList = Await.result(result, settings.timeout).toList
          ApiResponseUtil.toResponse(RespAllPeers(resultList))
        } catch {
          case e: Throwable => SidechainApiError(e)
        }
      }
    }
  }

  def connect: Route = (post & path("connect")) {
    withBasicAuth {
      _ => {
        entity(as[ReqConnect]) {
          body =>
            val address = body.host + ":" + body.port
            val maybeAddress = addressAndPortRegexp.findFirstMatchIn(address)
            maybeAddress match {
              case None =>
                ApiResponseUtil.toResponse(ErrorInvalidHost("Incorrect host and/or port.", JOptional.empty()))
              case Some(addressAndPort) =>
                Try(InetAddress.getByName(addressAndPort.group(1))) match {
                  case Failure(exception) => SidechainApiError(exception)
                  case Success(host) =>
                    val port = addressAndPort.group(2).toInt
                    networkController ! ConnectTo(PeerInfo.fromAddress(new InetSocketAddress(host, port)))
                    ApiResponseUtil.toResponse(RespConnect(host + ":" + port))
                }
            }
        }
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

  def disconnect: Route = (post & path("disconnect")) {
    withBasicAuth {
      _ => {
        entity(as[ReqDisconnect]) {
          body =>
            val address = body.host + ":" + body.port
            val maybeAddress = addressAndPortRegexp.findFirstMatchIn(address)
            maybeAddress match {
              case None =>
                ApiResponseUtil.toResponse(ErrorInvalidHost("Incorrect host and/or port.", JOptional.empty()))
              case Some(addressAndPort) =>
                val host = InetAddress.getByName(addressAndPort.group(1))
                val port = addressAndPort.group(2).toInt
                val peerAddress = new InetSocketAddress(host, port)
                // remove the peer info to prevent automatic reconnection attempts after disconnection.
                peerManager ! RemovePeer(peerAddress)
                // Disconnect the connection if present and active.
                // Note: `Blacklisted` name is misleading, because the message supposed to be used only during peer penalize
                // procedure. Actually inside NetworkController it looks for connection and emits `CloseConnection`.
                networkController ! DisconnectFromAddress(peerAddress)
                ApiResponseUtil.toResponse(RespDisconnect(host + ":" + port))
            }
        }
      }
    }
  }

  def stop: Route = (post & path("stop")) {
    withBasicAuth {
      _ => {
        if (app.stopAllInProgress.compareAndSet(false, true)) {
          try {
            // we are stopping the node, and since we will shortly be closing network services lets do in a separate thread
            // and give some time to the HTTP reply to be transmitted immediately in this thread
            new Thread(new Runnable() {
              override def run(): Unit = {
                log.info("Stop command triggered...")
                sleep(500)
                log.info("Calling core application stop...")
                app.sidechainStopAll(true)
                log.info("... core application stop returned")
              }
            }).start()

            ApiResponseUtil.toResponse(RespStop())

          } catch {
            case e: Throwable => SidechainApiError(e)
          }
        } else {
          log.warn("Stop node already in progress...")
          ApiResponseUtil.toResponse(ErrorStopNodeAlreadyInProgress("Stop node procedure already in progress", JOptional.empty()))
        }
      }
    }
  }

  def getNodeStorageVersions: Route = (post & path("storageVersions")) {
    try {
      val result = askActor[Map[String, String]](sidechainNodeViewHolderRef, GetStorageVersions)
        .map(x => RespGetNodeStorageVersions(x))
      val listOfVersions = Await.result(result, settings.timeout)
      ApiResponseUtil.toResponse(listOfVersions)
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

  def getSidechainId: Route = (post & path("sidechainId")) {
    try {
      val sidechainId = BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId))
      ApiResponseUtil.toResponse(RespGetSidechainId(sidechainId))
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

}

object SidechainNodeRestSchema {

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespAllPeers(peers: List[SidechainPeerNode]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class SidechainPeerNode(
                                             remoteAddress: String,
                                             localAddress: Option[String],
                                             lastHandshake: Long,
                                             lastMessage: Long,
                                             name: String,
                                             agentName: String,
                                             protocolVersion: String,
                                             connectionType: Option[String]
                                           )

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespBlacklistedPeers(addresses: Seq[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqConnect(host: String, port: Int)

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespConnect(connectedTo: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqDisconnect(host: String, port: Int)

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespDisconnect(disconnectedFrom: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqStop()

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespGetNodeStorageVersions(listOfVersions: Map[String, String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespStop() extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespGetSidechainId(sidechainId: String) extends SuccessResponse

}

object SidechainNodeErrorResponse {

  case class ErrorInvalidHost(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0401"
  }

  case class ErrorStopNodeAlreadyInProgress(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0402"
  }

}
