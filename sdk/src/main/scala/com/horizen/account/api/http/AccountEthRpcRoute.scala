package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.horizen.SidechainTypes
import com.horizen.account.api.rpc.handler.RpcHandler
import com.horizen.account.api.rpc.request.RpcRequest
import com.horizen.account.api.rpc.service.EthService
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.{ApiResponse, ApiResponseUtil, ErrorResponse, SidechainApiResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.node.NodeWalletBase
import scorex.core.settings.RESTApiSettings

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

case class AccountEthRpcRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                     (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] with SidechainTypes {

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])


  override val route: Route = (pathPrefix("eth_v1")) {
    ethRpc ~ ethOptions
  }

  /**
   * Returns the success / error response of called rpc method or error if method does not exist
   */
  def ethRpc: Route = (post) {
    entity(as[JsonNode])
    { body =>
      withNodeView { view =>
        var rpcHandler = new RpcHandler(new EthService(view));
        ApiResponseUtil.toResponseWithoutResultWrapper(rpcHandler.apply(new RpcRequest(body)));
      }
    }
  }

  def ethOptions: Route = (options) {
    complete("Allow: OPTIONS, POST");
  }
}
