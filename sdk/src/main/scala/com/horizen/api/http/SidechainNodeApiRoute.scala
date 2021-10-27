package com.horizen.api.http

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.http.SidechainNodeRestSchema._
import scorex.core.settings.RESTApiSettings

import scala.concurrent.{Await, ExecutionContext}
import scorex.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import scorex.core.network.peer.PeerInfo
import scorex.core.network.peer.PeerManager.ReceivableMessages.{Blacklisted, GetAllPeers, GetBlacklistedPeers, RemovePeer}
import scorex.core.utils.NetworkTimeProvider
import JacksonSupport._
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.SidechainNodeErrorResponse.ErrorInvalidHost
import com.horizen.serialization.Views
import java.util.{Optional => JOptional}

case class SidechainNodeApiRoute(peerManager: ActorRef,
                                 networkController: ActorRef,
                                 timeProvider: NetworkTimeProvider,
                                 override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute {

  override val route: Route = pathPrefix("node") {
    connect ~ allPeers ~ connectedPeers ~ blacklistedPeers ~ disconnect ~ nodeSyncStatus
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
        val address = body.host + ":" + body.port
        val maybeAddress = addressAndPortRegexp.findFirstMatchIn(address)
        maybeAddress match {
          case None =>
            ApiResponseUtil.toResponse(ErrorInvalidHost("Incorrect host and/or port.", JOptional.empty()))
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

  def disconnect: Route = (post & path("disconnect")) {
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
            networkController ! Blacklisted(peerAddress)
            ApiResponseUtil.toResponse(RespDisconnect(host + ":" + port))
        }
    }
  }


  def nodeSyncStatus: Route = (path("nodeSyncStatus") & post) {

    // how to get the status name ? will I determine like 'finished' if it is 100% and syncing otherwise??
    //   -->   FINISHED it's just a word by js file for indicating that it is Sync, so just pair another word if it is not


    // get the reference of the node with highest best block, then the height of this
        // how to make? search all peers and for each peers check and find the highest best_block ? no, maybe in the headers received that is a very fast at beginning

    // get the current height of the block itself, then calculate percentage // probably the simplest part

    // what is the node type?

    // error is maybe useless, the explorer needs it , but we don't ???


    // first thing is understand what mainchain return us from rpc calls
    // what mainchain can provide us
    // consider cheating / forking


    //Class invokated->  src/checkpoints.cpp
    // methods
    //double GuessVerificationProgress(const CCheckpointData& data, CBlockIndex *pindex, bool fSigchecks)


// Class checkpoint hardcoded ->   src/chainparams.cpp
     //checkpointData = (Checkpoints::CCheckpointData) {...


    /*
        As the node connect to the network, it is very fast for it to get the headers missing to its stack (if it is at 220 height and best_block is 400 it ll get 200 headers)
        ( there is also a mechanism of hardcoded checkpoints, so the node will start from a certain check point, let's understand better )
        , when it get the headers list it can calculate with that method an approximative percentage (in time) to get to completion, it is not based on ratio ( like 200/400 ),
        it mostly  considers all the transactions are in the blocks to come and provides a percentage
        ( so it probably consider how much the block made since the sync has started and how much it weill need for getting done)





     */



    ApiResponseUtil.toResponse( RespSyncInfo( NodeSyncInfo("Statusss",167633232, 56, 89565655, "no error at all", "a node of TYPE")))
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
  private[api] case class ReqDisconnect(host: String, port: Int)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespDisconnect(disconnectedFrom: String) extends SuccessResponse
// DIRAC 202
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespSyncInfo(syncStatus: NodeSyncInfo) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class NodeSyncInfo(status: String, blockChainHeight: Long,  syncPercentage: Int,   nodeHeight: Long, error: String, nodeType :String)

}

object SidechainNodeErrorResponse {

  case class ErrorInvalidHost(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0401"
  }

}