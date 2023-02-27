package com.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.databind.JsonNode
import com.horizen.account.api.rpc.handler.{RpcException, RpcHandler}
import com.horizen.account.api.rpc.request.{RpcId, RpcRequest}
import com.horizen.account.api.rpc.response.RpcResponseError
import com.horizen.account.api.rpc.service.EthService
import com.horizen.account.api.rpc.utils.{RpcCode, RpcError}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.serialization.EthJsonMapper
import com.horizen.account.state.MessageProcessor
import com.horizen.account.storage.AccountStateMetadataStorage
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.{SidechainApiResponse}
import com.horizen.api.http.route.SidechainApiRoute
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.utils.ClosableResourceHandler
import com.horizen.{SidechainSettings, SidechainTypes}
import io.horizen.evm.LevelDBDatabase
import sparkz.core.api.http.ApiDirectives
import sparkz.core.settings.RESTApiSettings
import sparkz.util.SparkzLogging

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.asScalaIteratorConverter
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

case class AccountEthRpcRoute(
    override val settings: RESTApiSettings,
    sidechainNodeViewHolderRef: ActorRef,
    networkControllerRef: ActorRef,
    sidechainSettings: SidechainSettings,
    params: NetworkParams,
    sidechainTransactionActorRef: ActorRef,
    metadataStorage: AccountStateMetadataStorage,
    stateDb: LevelDBDatabase,
    messageProcessors: Seq[MessageProcessor]
)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
    extends SidechainApiRoute[
      SidechainTypes#SCAT,
      AccountBlockHeader,
      AccountBlock,
      AccountFeePaymentsInfo,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView
    ]
      with SidechainTypes
      with ClosableResourceHandler
      with SparkzLogging
      with ApiDirectives {

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])
  override val route: Route = pathPrefix("ethv1") {
    ethRpc
  }
  private val rpcHandler = new RpcHandler(
    new EthService(
      sidechainNodeViewHolderRef,
      networkControllerRef,
      settings.timeout,
      params,
      sidechainSettings.ethService,
      sidechainSettings.sparkzSettings.network.maxIncomingConnections,
      getClientVersion,
      sidechainTransactionActorRef
    )
  )

  /**
   * Returns the success / error response of called rpc method or error if method does not exist
   */
  def ethRpc: Route = post {
    withBasicAuth {
      _ =>
        {
          entity(as[JsonNode]) { body =>
            val requests = if (body.isArray && !body.isEmpty) {
              // if the input json is an array a batch rpc request will be handled
              // the single rpc request will retrieve from the input json and they will be processed by rpcHandler
              // the position of the elements in the output will reflect their position in the input request
              body.iterator().asScala.toArray
            } else {
              // if the input json is not an array a single rpc request will be handled
              Array(body)
            }

            val responses = requests.map(json => Try.apply(new RpcRequest(json)).map(rpcHandler.apply) match {
              case Success(value) => value
              case Failure(exception: RpcException) => new RpcResponseError(new RpcId(), exception.error);
              case Failure(exception) =>
                log.trace(s"internal error on RPC call: $exception")
                new RpcResponseError(new RpcId(), RpcError.fromCode(RpcCode.InvalidRequest));
            })

            val json = if (responses.length > 1) {
              EthJsonMapper.serialize(responses)
            } else {
              EthJsonMapper.serialize(responses.head)
            }

            log.trace(s"RPC message response << $json")
            SidechainApiResponse(json);
          }
        }
    }
  }

  private def getClientVersion: String = {
    val default = "dev"
    val architecture = Try(System.getProperty("os.arch")).getOrElse(default)
    val javaVersion = Try(System.getProperty("java.specification.version")).getOrElse(default)
    val sdkPackage = this.getClass.getPackage
    val sdkTitle = sdkPackage.getImplementationTitle match {
      case null => default
      case title => Try(title.split(":")(1)).getOrElse(title)
    }
    val sdkVersion = sdkPackage.getImplementationVersion
    s"$sdkTitle/$sdkVersion/$architecture/jdk$javaVersion"
  }
}
