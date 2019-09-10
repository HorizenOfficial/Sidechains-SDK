package com.horizen.api.http

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.http.schema.SidechainNodeRestSchema.{ReqConnectPost, RespBlacklistedPeersPost, RespConnectPost, SidechainPeerNode}
import scorex.core.settings.RESTApiSettings

import scala.concurrent.{Await, ExecutionContext}
import scorex.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import scorex.core.network.peer.PeerInfo
import scorex.core.network.peer.PeerManager.ReceivableMessages.{GetAllPeers, GetBlacklistedPeers}
import scorex.core.utils.NetworkTimeProvider
import JacksonSupport._
import com.horizen.api.http.schema.INVALID_HOST_PORT
import com.horizen.serialization.SerializationUtil

case class SidechainNodeApiRoute(peerManager: ActorRef,
                                 networkController: ActorRef,
                                 timeProvider: NetworkTimeProvider,
                                 override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                (implicit val context: ActorRefFactory, override val ec : ExecutionContext) extends SidechainApiRoute {

  override val route : Route = (pathPrefix("node"))
    {connect ~ allPeers ~ connectedPeers ~ blacklistedPeers}

  private val addressAndPortRegexp = "([\\w\\.]+):(\\d{1,5})".r

  def allPeers: Route = (path("allPeers") & post) {
    try {
      val result = askActor[Map[InetSocketAddress, PeerInfo]](peerManager, GetAllPeers).map {
        _.map { case (address, peerInfo) =>
          SidechainPeerNode(address.toString, peerInfo.lastSeen, peerInfo.peerSpec.nodeName, peerInfo.connectionType.map(_.toString))
        }
      }

      val resultList = Await.result(result, settings.timeout).toList

      SidechainApiResponse(
        SerializationUtil.serializeWithResult(resultList)
      )

    }catch {
      case e : Throwable => SidechainApiError(e)
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

      SidechainApiResponse(
        SerializationUtil.serializeWithResult(resultList)
      )

    }catch {
      case e : Throwable => SidechainApiError(e)
    }
  }

  def connect : Route = (post & path("connect"))
  {
    entity(as[ReqConnectPost]){
      body =>
        var address = body.host+":"+body.port
        val maybeAddress = addressAndPortRegexp.findFirstMatchIn(address)
        maybeAddress match {
          case None =>
            SidechainApiResponse(
              SerializationUtil.serializeErrorWithResult(INVALID_HOST_PORT().apiCode, "Incorrect host and/or port.", "")
            )
          case Some(addressAndPort) =>
            val host = InetAddress.getByName(addressAndPort.group(1))
            val port = addressAndPort.group(2).toInt
            networkController ! ConnectTo(PeerInfo.fromAddress(new InetSocketAddress(host, port)))
            SidechainApiResponse(
              SerializationUtil.serializeWithResult(
                RespConnectPost(host+":"+port)
              )
            )
        }
    }
  }

  def blacklistedPeers: Route = (path("blacklistedPeers") & post) {
    try {
      val result = askActor[Seq[InetAddress]](peerManager, GetBlacklistedPeers)
        .map(x => RespBlacklistedPeersPost(x.map(_.toString)))

      val resultList = Await.result(result, settings.timeout)

      SidechainApiResponse(
        SerializationUtil.serializeWithResult(resultList)
      )
    }catch {
      case e : Throwable => SidechainApiError(e)
    }
  }

}
