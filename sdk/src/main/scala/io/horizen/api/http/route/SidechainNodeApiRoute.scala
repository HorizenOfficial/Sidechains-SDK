package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetStorageVersions
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.node.AccountNodeView
import io.horizen.account.state.AccountState
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.route.SidechainNodeErrorResponse.{ErrorInvalidHost, ErrorStopNodeAlreadyInProgress}
import io.horizen.api.http.route.SidechainNodeRestSchema._
import io.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiError, SuccessResponse}
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.json.Views
import io.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import io.horizen.params.NetworkParams
import io.horizen.transaction.Transaction
import io.horizen.utils.BytesUtils
import io.horizen.utxo.mempool.SidechainMemoryPool
import io.horizen.{AbstractSidechainApp, SidechainNodeViewBase}
import sparkz.core.api.http.ApiResponse
import sparkz.core.network.ConnectedPeer
import sparkz.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers}
import sparkz.core.network.peer.PeerInfo
import sparkz.core.network.peer.PeerManager.ReceivableMessages._
import sparkz.core.network.peer.PenaltyType.CustomPenaltyDuration
import sparkz.core.settings.RESTApiSettings
import sparkz.core.utils.NetworkTimeProvider

import java.io.File
import java.lang.Thread.sleep
import java.net.{InetAddress, InetSocketAddress}
import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.sys.process._
import scala.util.{Failure, Success, Try}
import scala.xml.XML

case class SidechainNodeApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](peerManager: ActorRef,
                                 networkController: ActorRef,
                                 timeProvider: NetworkTimeProvider,
                                 override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, app: AbstractSidechainApp, params: NetworkParams)
                                (implicit val context: ActorRefFactory, val ec: ExecutionContext, override val tag: ClassTag[NV]) extends SidechainApiRoute[TX, H, PM, FPI, NH, NS, NW, NP, NV] {


  override val route: Route = pathPrefix("node") {

    connect ~ allPeers ~ connectedPeers ~ blacklistedPeers ~ disconnect ~ stop ~ getNodeStorageVersions ~ getSidechainId ~ peerByAddress ~ addToBlacklist ~ removeFromBlacklist ~ removePeer ~ nodeInfo
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

  def peerByAddress: Route = (path("peer") & post) {
    try {
      entity(as[ReqWithAddress]) { bodyRequest =>
        val maybeAddress = addressAndPortRegexp.findFirstMatchIn(bodyRequest.address)
        maybeAddress match {
          case None => SidechainApiError(s"address $maybeAddress is not well formatted")

          case Some(addressAndPort) =>
            val host = InetAddress.getByName(addressAndPort.group(1))
            val port = addressAndPort.group(2).toInt

            val address = new InetSocketAddress(host, port)

            val result = askActor[PeerInfo](peerManager, GetPeer(address)).map(peerInfo =>
              SidechainPeerNode(address.toString, None, peerInfo.lastHandshake, 0, peerInfo.peerSpec.nodeName, peerInfo.peerSpec.agentName, peerInfo.peerSpec.protocolVersion.toString, peerInfo.connectionType.map(_.toString)))

            val peerInfo = Await.result(result, settings.timeout)
            ApiResponseUtil.toResponse(RespGetPeer(peerInfo))
        }
      }
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

  //todo david

  /*
  [X] Node name
  [ ] Node types - forger, submitter, signer, simple node
  [X] Sdk version
  [X] Sc ID
  [X] Sc type - ceasable/non-ceasable
  [X] Sc model - UTXO/Account
  [X] Sc block height
  [X] Sc consensus epoch
  [X] Sc withdrawal epoch
  [X] Sc environment - on which network is node currently - mainnet/testnet/regtest
  [ ] Sc node version
  [X] Number of connected peers
  [X] Number of peers
  [X] Number of blacklisted peers
  [X] Tx mempool - executable & non executable - only for account model, UTXO has unified
  [X] maxMemPoolSlots - if it’s configurable
  [ ] Last baseFeePerGas - for account model only
  [ ] Forward transfer min fee
  [ ] Quality of last certificate - account only?
  [ ] Backward transfer fee
  [ ] Last MC reference hash
  * */
  def nodeInfo: Route = (path("info") & post) {
    try {
      applyOnNodeView { nodeView =>
        var allTransactionSize = 0
        var nonExecTransactionSize = 0
        var execTransactionSize = 0
        var scModel = ""



        allTransactionSize = nodeView.getNodeMemoryPool.getTransactions.size()


        val sidechainId = BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId))
        val isNonCeasing = params.isNonCeasing
        val withdrawalEpochLength = params.withdrawalEpochLength

        val nodeName = app.settings.network.nodeName
        val a1 = app.settings.network.agentName
        val a2 = app.settings.network.appVersion // what is app version? sc node version or sdk version?
        val a3 = app.settings.network.knownPeers
        val a4 = app.settings.network.declaredAddress
        val a5 = app.settings.ntp.server
        val a6 = app.settings.restApi.bindAddress

        val a7 = app.sidechainSettings.accountMempool.maxMemPoolSlots
        val a8 = app.sidechainSettings.withdrawalEpochCertificateSettings.certificateSigningIsEnabled
        val a32 = app.sidechainSettings.forger.automaticForging
        val a9 = app.sidechainSettings.withdrawalEpochCertificateSettings.submitterIsEnabled
        val a10 = app.sidechainSettings.ethService.globalRpcGasCap
        val a11 = app.sidechainSettings.mempool.maxSize
        val a12 = app.sidechainSettings.websocketClient.enabled

        val a13 = app.chainInfo.mainnetId
        val a14 = app.chainInfo.testnetId
        val a15 = app.chainInfo.regtestId
        val scEnv = app.sidechainSettings.genesisData.mcNetwork  //on which network is the node currently (regtest in our case)

        val chainId = app.params.chainId
        val scId = BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId))
        val a18 = app.nodeViewSynchronizer
        val a19 = app.nodeViewHolderRef
        val a19asd = app.mainchainNodeChannel.getTopQualityCertificates(sidechainId) //java.lang.IllegalStateException: The web socket channel must be not null.

        var blacklistedNum = -1
        val resultBlacklisted = askActor[Seq[InetAddress]](peerManager, GetBlacklistedPeers)
          .map(blacklistedPeers => {
            blacklistedNum = blacklistedPeers.length
            println(s"There are $blacklistedNum blacklisted peers.")
          })


        var connectedToNum = -1
        val resultConnected = askActor[Seq[ConnectedPeer]](networkController, GetConnectedPeers)
          .map(connectedPeers => {
            connectedToNum = connectedPeers.length
            println(s"There are $connectedToNum connected peers.")
          })

        Await.result(resultBlacklisted, settings.timeout)
        Await.result(resultConnected, settings.timeout)



        //sdk version
        val sdkPath = sys.env.getOrElse("SIDECHAIN_SDK", "") + "/pom.xml"
        val pomFile = new File(sdkPath)
        val pomXml = XML.loadFile(pomFile)
        val versionTag = (pomXml \ "version").head
        val sdkVersion = versionTag.text
        println(sdkVersion)

        //node version
        val nodeVersion = "node --version".!!.trim
        println(nodeVersion)

        val scBlockHeight =nodeView.getNodeHistory.getCurrentHeight

        val mempoolSize = nodeView.getNodeMemoryPool.getTransactions.size()
        if (nodeView.isInstanceOf[AccountNodeView]) {
          val e = nonExecTransactionSize = nodeView.getNodeMemoryPool.asInstanceOf[AccountMemoryPool].getNonExecutableTransactions.size()
          val e1 =execTransactionSize = nodeView.getNodeMemoryPool.asInstanceOf[AccountMemoryPool].getExecutableTransactions.size()

          val e3 =nodeView.getNodeState.asInstanceOf[AccountState].getConsensusEpochNumber
          val e4 =nodeView.getNodeState.asInstanceOf[AccountState].getWithdrawalEpochInfo.epoch
  //          val e5 =nodeView.getNodeState.asInstanceOf[AccountState].getTopQualityCertificate(nodeView.getNodeState.asInstanceOf[AccountState].getWithdrawalEpochInfo.epoch).get.quality
          scModel = "Account"
        }
        else {
            nonExecTransactionSize = nodeView.getNodeMemoryPool.asInstanceOf[SidechainMemoryPool].size
            execTransactionSize = nodeView.getNodeMemoryPool.asInstanceOf[SidechainMemoryPool].usedSizeKBytes
            execTransactionSize = nodeView.getNodeMemoryPool.asInstanceOf[SidechainMemoryPool].usedPercentage
            scModel = "UTXO"
        }


        val acc = app.sidechainSettings.forger.allowedForgersList
        app.sidechainSettings.withdrawalEpochCertificateSettings.signersPublicKeys


  //        case class ForgerKeysData(
  //                                   blockSignProposition: String,
  //                                   vrfPublicKey: String,
  //                                 ) extends SensitiveStringer

        ApiResponseUtil.toResponse(RespNodeInfo(
          nodeName = nodeName,
          nodeType = Option.empty,
          sdkVersion = Option(sdkVersion),
          scId = Option(scId),
          scType = Option(if (isNonCeasing) "non ceasing" else "ceasing"),
          scModel = Option(scModel),
          scBlockHeight = Option(scBlockHeight),
          scConcensusEpoch = Option.empty,
          scWithdrawalEpochLength = Option.empty,
          scEnv = Option(scEnv),
          scNodeVersion = Option.empty,
          numberOfPeers = Option.empty,
          numberOfConnectedPeers = Option(connectedToNum),
          numberOfBlacklistedPeers = Option(blacklistedNum),
          mempoolSize = Option(mempoolSize),
          executableTxSize = Option(execTransactionSize),
          nonExecutableTxSize = Option(nonExecTransactionSize),
        ))


      }
    } catch {
      case e: Throwable => SidechainApiError(e)
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

  def removePeer: Route = (path("removePeer") & post & withBasicAuth) {
    _ => {
      try {
        entity(as[ReqWithAddress]) { bodyRequest =>
          val maybeAddress = addressAndPortRegexp.findFirstMatchIn(bodyRequest.address)

          maybeAddress match {
            case None => SidechainApiError(s"address $maybeAddress is not well formatted")

            case Some(addressAndPort) =>
              val host = InetAddress.getByName(addressAndPort.group(1))
              val port = addressAndPort.group(2).toInt
              val peerAddress = new InetSocketAddress(host, port)
              peerManager ! RemovePeer(peerAddress)
              networkController ! DisconnectFromAddress(peerAddress)
              ApiResponse.OK
          }
        }
      } catch {
        case e: Throwable => SidechainApiError(e)
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

  def addToBlacklist: Route = (post & path("addToBlacklist") & withBasicAuth) {
    _ => {
      try {
        entity(as[ReqAddToBlacklist]) { bodyRequest =>
          val peerAddress = bodyRequest.address
          val banDuration = bodyRequest.durationInMinutes

          if (banDuration <= 0) {
            SidechainApiError(s"duration must be greater than 0; $banDuration not allowed")
          } else {
            addressAndPortRegexp.findFirstMatchIn(peerAddress) match {
              case None => SidechainApiError(s"address $peerAddress is not well formatted")

              case Some(addressAndPort) =>
                val host = InetAddress.getByName(addressAndPort.group(1))
                val port = addressAndPort.group(2).toInt
                val peerAddress = new InetSocketAddress(host, port)
                peerManager ! AddToBlacklist(peerAddress, Some(CustomPenaltyDuration(banDuration)))
                networkController ! DisconnectFromAddress(peerAddress)
                ApiResponse.OK
            }
          }
        }
      } catch {
        case e: Throwable => SidechainApiError(e)
      }
    }
  }

  def removeFromBlacklist: Route = (path("removeFromBlacklist") & post & withBasicAuth) {
    _ => {
      try {
        entity(as[ReqWithAddress]) { bodyRequest =>
          val maybeAddress = addressAndPortRegexp.findFirstMatchIn(bodyRequest.address)

          maybeAddress match {
            case None => SidechainApiError(s"address $maybeAddress is not well formatted")

            case Some(addressAndPort) =>
              val host = InetAddress.getByName(addressAndPort.group(1))
              val port = addressAndPort.group(2).toInt
              peerManager ! RemoveFromBlacklist(new InetSocketAddress(host, port))
              ApiResponse.OK
          }
        }
      } catch {
        case e: Throwable => SidechainApiError(e)
      }
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
  private[horizen] case class RespNodeInfo(
                                         nodeName: String,
                                         nodeType: Option[String],
                                         sdkVersion: Option[String],
                                         scId: Option[String],
                                         scType: Option[String],
                                         scModel: Option[String],
                                         scBlockHeight: Option[Int],
                                         scConcensusEpoch: Option[String],
                                         scWithdrawalEpochLength: Option[String],
                                         scEnv: Option[String],
                                         scNodeVersion: Option[String],
                                         numberOfPeers: Option[Int],
                                         numberOfConnectedPeers: Option[Int],
                                         numberOfBlacklistedPeers: Option[Int],
                                         mempoolSize: Option[Int],
                                         executableTxSize: Option[Int],
                                         nonExecutableTxSize: Option[Int],
                                     )  extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespBlacklistedPeers(addresses: Seq[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class RespGetPeer(peer: SidechainPeerNode) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqConnect(host: String, port: Int)

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class ReqAddToBlacklist(address: String, durationInMinutes: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class ReqWithAddress(address: String)

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
