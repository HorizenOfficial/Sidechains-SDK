package com.horizen.api.http

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import io.circe.generic.auto._
import scorex.core.network.NetworkController.ReceivableMessages.ConnectTo
import scorex.core.network.peer.PeerInfo

case class SidechainNodeApiRoute(networkController: ActorRef, override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                (implicit val context: ActorRefFactory, override val ec : ExecutionContext) extends SidechainApiRoute {

  override val route : Route = (pathPrefix("node"))
            {connect ~ getAllPeersInfo}

  private val addressAndPortRegexp = "([\\w\\.]+):(\\d{1,5})".r

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

  def getAllPeersInfo : Route = (post & path("getAllPeer sInfo"))
  {
    ApiResponse.OK
  }

}
