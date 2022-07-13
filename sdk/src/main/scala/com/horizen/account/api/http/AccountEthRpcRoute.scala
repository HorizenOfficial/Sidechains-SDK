package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainSettings, SidechainState, SidechainTypes, SidechainWallet}
import com.horizen.account.api.rpc.handler.RpcHandler
import com.horizen.account.api.rpc.request.RpcRequest
import com.horizen.account.api.rpc.service.EthService
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.state.{AccountState, AccountStateView, MessageProcessor}
import com.horizen.account.storage.{AccountStateMetadataStorage, AccountStateMetadataStorageView}
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.{ApiResponse, ApiResponseUtil, ErrorResponse, SidechainApiResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.evm.{LevelDBDatabase, StateDB}
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import scorex.core.NodeViewHolder
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.settings.RESTApiSettings

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

case class AccountEthRpcRoute(override val settings: RESTApiSettings,
                              sidechainNodeViewHolderRef: ActorRef,
                              sidechainSettings: SidechainSettings,
                              params: NetworkParams,
                              sidechainTransactionActorRef: ActorRef,
                              metadataStorage: AccountStateMetadataStorage,
                              stateDb: LevelDBDatabase,
                              messageProcessors: Seq[MessageProcessor],
                             )
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


  override val route: Route = (pathPrefix("ethv1")) {
    ethRpc ~ ethOptions
  }


  type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]

  def applyOnAccountView[R](functionToBeApplied: NV => R): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? NodeViewHolder.ReceivableMessages.GetDataFromCurrentView(functionToBeApplied)).asInstanceOf[Future[R]]
      val result = Await.result[R](res, settings.timeout)
      result
    }
    catch {
      case e: Exception => throw new Exception(e)
    }

  }
  /**
   * Returns the success / error response of called rpc method or error if method does not exist
   */
  def ethRpc: Route = (post) {
    entity(as[JsonNode])
    { body =>
      applyOnAccountView { view =>
        // TODO: optimize usage of rpcHandler (no need to create an object from scratch every time)
        // TODO: improve the usage of node and state views. Possibly put the getters into the RpcHandler
        val currentView: AccountStateView = view.state.getView
        val rpcHandler = new RpcHandler(new EthService(currentView, view, params, sidechainSettings, sidechainTransactionActorRef));
        val res = rpcHandler.apply(new RpcRequest(body))
        currentView.close()
        ApiResponseUtil.toResponseWithoutResultWrapper(res);

      }
    }
  }

  def ethOptions: Route = (options) {
    complete("Allow: OPTIONS, POST");
  }
}
