package com.horizen.api.http

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import scorex.core.api.http.PeersApiRoute.{BlacklistedPeers, PeerInfoResponse}
import scorex.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import scorex.core.network.peer.PeerInfo
import scorex.core.network.peer.PeerManager.ReceivableMessages.{GetAllPeers, GetBlacklistedPeers}
import scorex.core.utils.NetworkTimeProvider

case class SidechainNodeApiRoute(peerManager: ActorRef,
                                 networkController: ActorRef,
                                 timeProvider: NetworkTimeProvider,
                                 override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                (implicit val context: ActorRefFactory, override val ec : ExecutionContext) extends SidechainApiRoute {

  override val route : Route = (pathPrefix("node"))
            {connect ~ allPeers ~ connectedPeers ~ blacklistedPeers}

  private val addressAndPortRegexp = "([\\w\\.]+):(\\d{1,5})".r

  def allPeers: Route = (path("all") & get) {
    val result = askActor[Map[InetSocketAddress, PeerInfo]](peerManager, GetAllPeers).map {
      _.map { case (address, peerInfo) =>
        PeerInfoResponse.fromAddressAndInfo(address, peerInfo)
      }
    }
    ApiResponse(result)
  }

  def connectedPeers: Route = (path("connected") & get) {
    val result = askActor[Seq[PeerInfo]](networkController, GetConnectedPeers).map {
      _.map { peerInfo =>
        PeerInfoResponse(
          address = peerInfo.peerSpec.address.map(_.toString).getOrElse(""),
          lastSeen = peerInfo.lastSeen,
          name = peerInfo.peerSpec.nodeName,
          connectionType = peerInfo.connectionType.map(_.toString)
        )
      }
    }
    ApiResponse(result)
  }

  def connect : Route = (post & path("connect"))
  {
    case class ConnectRequest(address : String)
    entity(as[String]){
      body =>
        ApiInputParser.parseInput[ConnectRequest](body) match {
          case Success(req) =>
            var address = req.address
            val maybeAddress = addressAndPortRegexp.findFirstMatchIn(address)
            maybeAddress match {
              case None => ApiError.BadRequest
              case Some(addressAndPort) =>
                val host = InetAddress.getByName(addressAndPort.group(1))
                val port = addressAndPort.group(2).toInt
                networkController ! ConnectTo(PeerInfo.fromAddress(new InetSocketAddress(host, port)))
                ApiResponse.OK
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
    }
  }

  def blacklistedPeers: Route = (path("blacklisted") & get) {
    val result = askActor[Seq[InetAddress]](peerManager, GetBlacklistedPeers)
      .map(x => BlacklistedPeers(x.map(_.toString)).asJson)
    ApiResponse(result)
  }

}
