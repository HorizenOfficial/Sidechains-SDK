package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.databind.JsonNode
import com.horizen.account.api.rpc.handler.RpcHandler
import com.horizen.account.api.rpc.request.{RpcBatchRequest, RpcRequest}
import com.horizen.account.api.rpc.response.RpcResponseError
import com.horizen.account.api.rpc.service.EthService
import com.horizen.account.api.rpc.utils.{RpcCode, RpcError}
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state.MessageProcessor
import com.horizen.account.storage.AccountStateMetadataStorage
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.{SidechainApiResponse, SidechainApiRoute}
import com.horizen.evm.LevelDBDatabase
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.ClosableResourceHandler
import com.horizen.{SidechainSettings, SidechainTypes}
import scorex.util.ScorexLogging
import sparkz.core.api.http.ApiDirectives
import sparkz.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class AccountEthRpcRoute(
    override val settings: RESTApiSettings,
    sidechainNodeViewHolderRef: ActorRef,
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
      with ScorexLogging
      with ApiDirectives {

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])
  override val route: Route = pathPrefix("ethv1") {
    ethRpc ~ ethOptions
  }
  val rpcHandler = new RpcHandler(
    new EthService(
      sidechainNodeViewHolderRef,
      settings.timeout,
      params,
      sidechainSettings,
      sidechainTransactionActorRef
    )
  )

  /**
   * Returns the success / error response of called rpc method or error if method does not exist
   */
  def ethRpc: Route = post {
    withAuth {
      entity(as[JsonNode]) { body =>

        val req =
          if(body.isArray && body.isEmpty) {
            new RpcResponseError(null, RpcError.fromCode(RpcCode.InvalidRequest, null))}
          else if(body.isArray()) {
            new RpcBatchRequest(body)}
          else {
            new RpcRequest(body)}
        log.debug(s"request >> $body")

        val res = {
          if(req.isInstanceOf[RpcResponseError]) {
            req}
          else if(req.isInstanceOf[RpcRequest]) {
            rpcHandler.apply(req.asInstanceOf[RpcRequest])}
          else if(req.isInstanceOf[RpcBatchRequest]) {
            rpcHandler.apply(req.asInstanceOf[RpcBatchRequest])}
        }

        val json = SerializationUtil.serialize(res)

        log.debug(s"response << $json")
        SidechainApiResponse(json);
      }
    }
  }

  def ethOptions: Route = options {
    complete("Allow: OPTIONS, POST")
  }
}
