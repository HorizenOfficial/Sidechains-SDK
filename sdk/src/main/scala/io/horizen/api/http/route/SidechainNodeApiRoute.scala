package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetStorageVersions
import io.horizen.account.api.rpc.service.RpcUtils
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
import io.horizen.utxo.node.SidechainNodeView
import io.horizen.utxo.state.SidechainState
import io.horizen.{AbstractSidechainApp, SidechainNodeViewBase}
import sparkz.core.api.http.ApiResponse
import sparkz.core.network.ConnectedPeer
import sparkz.core.network.NetworkController.ReceivableMessages.{ConnectTo, GetConnectedPeers, DisconnectFromNode}
import sparkz.core.network.peer.PeerInfo
import sparkz.core.network.peer.PeerManager.ReceivableMessages._
import sparkz.core.network.peer.PenaltyType.CustomPenaltyDuration
import sparkz.core.settings.RESTApiSettings
import sparkz.core.utils.NetworkTimeProvider

import java.lang.Thread.sleep
import java.net.{InetAddress, InetSocketAddress}
import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

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
                                 override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, app: AbstractSidechainApp, params: NetworkParams, appVersion: String = "")
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

  def nodeInfo: Route = (path("info") & post) {
    withBasicAuth {
      _ => {
        withNodeView { sidechainNodeView =>
          var nonExecTransactionSize = 0
          var execTransactionSize = 0

          val sidechainId = BytesUtils.toHexString(BytesUtils.reverseBytes(params.sidechainId))

          val nodeName = app.settings.network.nodeName
          val agentName = app.settings.network.agentName
          val protocolVersion = app.settings.network.appVersion

          val scEnv = app.sidechainSettings.genesisData.mcNetwork //on which network is the node currently - mainnet/testnet/regtest

          //blacklisted peers
          var blacklistedNum = -1
          val resultBlacklisted = askActor[Seq[InetAddress]](peerManager, GetBlacklistedPeers)
            .map(blacklistedPeers => {
              blacklistedNum = blacklistedPeers.length
            })

          //connected peers
          var connectedToNum = -1
          val resultConnected = askActor[Seq[ConnectedPeer]](networkController, GetConnectedPeers)
            .map(connectedPeers => {
              connectedToNum = connectedPeers.length
            })

          //all peers
          var allPeersNum = 0
          val resultAllPeers = askActor[Map[InetSocketAddress, PeerInfo]](peerManager, GetAllPeers).map {
            _.map {
              case (_, _) =>
                allPeersNum += 1
            }
          }

          Await.result(resultBlacklisted, settings.timeout)
          Await.result(resultConnected, settings.timeout)
          Await.result(resultAllPeers, settings.timeout)

          val scBlockHeight = sidechainNodeView.getNodeHistory.getCurrentHeight

          val lastScBlockId = sidechainNodeView.getNodeHistory.getLastBlockIds(1)
          val lastScBlock = sidechainNodeView.getNodeHistory.getBlockById(lastScBlockId.get(0))
          var lastMcBlockReferenceHash = ""
          if (!lastScBlock.isEmpty && lastScBlock.get().mainchainBlockReferencesData.nonEmpty)
            lastMcBlockReferenceHash = BytesUtils.toHexString(lastScBlock.get().mainchainBlockReferencesData.head.headerHash)

          var withdrawalEpochNum = -1
          var consensusEpoch = -1
          var epochForgersStake: Long = -1

          var certQuality: Long = -1
          var certEpoch: Int = -1
          var certBtrFee: Long = -1
          var certFtMinAmount: Long = -1
          var certHash: String = ""
          sidechainNodeView match {
            case viewAccount: AccountNodeView =>
              nonExecTransactionSize = viewAccount.getNodeMemoryPool.asInstanceOf[AccountMemoryPool].getNonExecutableTransactions.size()
              execTransactionSize = viewAccount.getNodeMemoryPool.asInstanceOf[AccountMemoryPool].getExecutableTransactions.size()

              withdrawalEpochNum = viewAccount.getNodeState.asInstanceOf[AccountState].getWithdrawalEpochInfo.epoch

              consensusEpoch = viewAccount.getNodeState.asInstanceOf[AccountState].getCurrentConsensusEpochInfo._2.epoch
              epochForgersStake = viewAccount.getNodeState.asInstanceOf[AccountState].getCurrentConsensusEpochInfo._2.forgersStake

              sidechainNodeView.getNodeState.asInstanceOf[AccountState].lastCertificateReferencedEpoch match {
                case Some(referencedEpoch) =>
                  viewAccount.getNodeState.getTopQualityCertificate(referencedEpoch) match {
                    case Some(cert) =>
                      certEpoch = cert.epochNumber
                      certQuality = cert.quality
                      certBtrFee = cert.btrFee
                      certFtMinAmount = cert.ftMinAmount
                      certHash = BytesUtils.toHexString(cert.hash)
                    case _ =>
                  }
                case _ =>
              }
            case viewSidechain: SidechainNodeView =>
              withdrawalEpochNum = viewSidechain.getNodeState.asInstanceOf[SidechainState].getWithdrawalEpochInfo.epoch

              consensusEpoch = viewSidechain.getNodeState.asInstanceOf[SidechainState].getCurrentConsensusEpochInfo._2.epoch
              epochForgersStake = viewSidechain.getNodeState.asInstanceOf[SidechainState].getCurrentConsensusEpochInfo._2.forgersStake

              viewSidechain.getNodeState.asInstanceOf[SidechainState].lastCertificateReferencedEpoch() match {
                case Some(referencedEpoch) =>
                  viewSidechain.getNodeState.asInstanceOf[SidechainState].certificate(referencedEpoch) match {
                    case Some(cert) =>
                      certEpoch = cert.epochNumber
                      certQuality = cert.quality
                      certBtrFee = cert.btrFee
                      certFtMinAmount = cert.ftMinAmount
                      certHash = BytesUtils.toHexString(cert.hash)
                    case _ =>
                  }
                case _ =>
              }
          }
          val numOfTxInMempool = sidechainNodeView.getNodeMemoryPool.getSize

          val errorLines: Array[String] = getErrorLogs
          val nodeTypes = getNodeTypes

          ApiResponseUtil.toResponse(RespNodeInfo(
            nodeName = nodeName,
            nodeType = nodeTypes,
            protocolVersion = protocolVersion,
            agentName = agentName,
            sdkVersion = RpcUtils.getClientVersion(appVersion),
            nodeVersion = if (appVersion != "") Option(appVersion) else Option.empty,
            scId = sidechainId,
            scType = if (params.isNonCeasing) "non ceasing" else "ceasing",
            scModel = if (sidechainNodeView.isInstanceOf[SidechainNodeView]) "UTXO" else "Account",
            scBlockHeight = scBlockHeight,
            scConsensusEpoch = consensusEpoch,
            epochForgersStake = epochForgersStake,
            nextBaseFee = if (sidechainNodeView.isInstanceOf[AccountNodeView]) Option(sidechainNodeView.getNodeState.asInstanceOf[AccountState].getView.getNextBaseFee) else Option.empty,
            scWithdrawalEpochLength = params.withdrawalEpochLength,
            scWithdrawalEpochNum = withdrawalEpochNum,
            scEnv = scEnv,
            lastMcBlockReferenceHash = if (lastMcBlockReferenceHash != "") Option(lastMcBlockReferenceHash) else Option.empty,
            numberOfPeers = allPeersNum,
            numberOfConnectedPeers = if (connectedToNum != -1) Option(connectedToNum) else Option.empty,
            numberOfBlacklistedPeers = if (blacklistedNum != -1) Option(blacklistedNum) else Option.empty,
            maxMemPoolSlots = if (sidechainNodeView.isInstanceOf[AccountNodeView]) Option(app.sidechainSettings.accountMempool.maxMemPoolSlots) else Option.empty,
            numOfTxInMempool = numOfTxInMempool,
            mempoolUsedSizeKBytes = if (sidechainNodeView.isInstanceOf[SidechainNodeView]) Option(sidechainNodeView.getNodeMemoryPool.asInstanceOf[SidechainMemoryPool].usedSizeKBytes) else Option.empty,
            mempoolUsedPercentage = if (sidechainNodeView.isInstanceOf[SidechainNodeView]) Option(sidechainNodeView.getNodeMemoryPool.asInstanceOf[SidechainMemoryPool].usedPercentage) else Option.empty,
            executableTxSize = if (sidechainNodeView.isInstanceOf[AccountNodeView]) Option(execTransactionSize) else Option.empty,
            nonExecutableTxSize = if (sidechainNodeView.isInstanceOf[AccountNodeView]) Option(nonExecTransactionSize) else Option.empty,
            lastCertQuality = if (certQuality != -1) Option(certQuality) else Option.empty,
            lastCertEpoch = if (certEpoch != -1) Option(certEpoch) else Option.empty,
            lastCertBtrFee = if (certBtrFee != -1) Option(certBtrFee) else Option.empty,
            lastCertFtMinAmount = if (certFtMinAmount != -1) Option(certFtMinAmount) else Option.empty,
            lastCertHash = if (certHash != "") Option(certHash) else Option.empty,
            errors = if (errorLines != null) Option(errorLines) else Option.empty
          ))
        }
      }
    }
  }

  private def getErrorLogs: Array[String] = {
    val logFilePath = app.sidechainSettings.sparkzSettings.logDir + "/" + app.sidechainSettings.logInfo.logFileName
    var errorLogs: Array[String] = null
    var source: Option[Source] = None
    try {
      source = Some(Source.fromFile(logFilePath))
      errorLogs = source.get.getLines().filter(_.contains("[ERROR]")).toArray
    } catch {
      case e: Exception =>
        log.debug(e.getMessage)
    } finally {
      source.foreach(_.close())
    }
    errorLogs
  }

  private def getNodeTypes: String = {
    var nodeTypes = ""
    if (app.sidechainSettings.forger.automaticForging)
      nodeTypes += "forger"
    if (app.sidechainSettings.withdrawalEpochCertificateSettings.certificateSigningIsEnabled)
      nodeTypes += ",signer"
    if (app.sidechainSettings.withdrawalEpochCertificateSettings.submitterIsEnabled)
      nodeTypes += ",submitter"
    if (nodeTypes == "")
      nodeTypes = "simple node"
    if (nodeTypes.charAt(0) == ',')
      nodeTypes = nodeTypes.stripPrefix(",")

    nodeTypes
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
          val peerAddress = bodyRequest.address
          Try(InetAddress.getByName(peerAddress)) match {
            case Failure(exception) =>
              SidechainApiError(s"address $peerAddress is not well formatted: ${exception.getMessage}")

            case Success(address) =>
              peerManager ! RemoveFromBlacklist(address)
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
                networkController ! DisconnectFromNode(peerAddress)
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
                                         nodeType: String,
                                         protocolVersion: String,
                                         agentName: String,
                                         sdkVersion: String,
                                         nodeVersion: Option[String],
                                         scId: String,
                                         scType: String,
                                         scModel: String,
                                         scBlockHeight: Int,
                                         scConsensusEpoch: Int,
                                         epochForgersStake: Long,
                                         nextBaseFee: Option[BigInt],
                                         scWithdrawalEpochLength: Int,
                                         scWithdrawalEpochNum: Int,
                                         scEnv: String,
                                         lastMcBlockReferenceHash: Option[String],
                                         numberOfPeers: Int,
                                         numberOfConnectedPeers: Option[Int],
                                         numberOfBlacklistedPeers: Option[Int],
                                         maxMemPoolSlots : Option[Int],
                                         numOfTxInMempool: Int,
                                         mempoolUsedSizeKBytes: Option[Int],
                                         mempoolUsedPercentage: Option[Int],
                                         executableTxSize: Option[Int],
                                         nonExecutableTxSize: Option[Int],
                                         lastCertEpoch : Option[Int],
                                         lastCertQuality : Option[Long],
                                         lastCertBtrFee : Option[Long],
                                         lastCertFtMinAmount : Option[Long],
                                         lastCertHash : Option[String],
                                         errors: Option[Array[String]]
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
