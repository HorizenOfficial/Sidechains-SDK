package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.{JsonUnwrapped, JsonView}
import com.horizen.SidechainTypes
import com.horizen.account.api.http.AccountBlockErrorResponse.ErrorNoBlockFound
import com.horizen.account.api.http.AccountBlockRestScheme.{ReqGetForwardTransfersRequests, RespAllForwardTransfers}
import com.horizen.account.api.rpc.types.ForwardTransfersView
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.transaction.mainchain.ForwardTransfer
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class AccountBlockApiRoute(override val settings: RESTApiSettings,
                                sidechainNodeViewHolderRef: ActorRef,
                                sidechainTransactionActorRef: ActorRef,
                                companion: SidechainAccountTransactionsCompanion,
                                params: NetworkParams)
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


  override val route: Route = pathPrefix("block") {
    getForwardTransfers
  }

  def getForwardTransfers: Route = (post & path("getForwardTransfers")) {
    entity(as[ReqGetForwardTransfersRequests]) { body =>
      withNodeView { sidechainNodeView =>
        val block = getBlockByEpochNumber(sidechainNodeView, body.epochNum)
        if (block == null) ApiResponseUtil.toResponse(ErrorNoBlockFound("ErrorNoBlockFound", JOptional.empty()))
        else {
          var transactions: Seq[ForwardTransfer] = Seq()
          for (refDataWithFTs <- block.mainchainBlockReferencesData) {
            refDataWithFTs.sidechainRelatedAggregatedTransaction match {
              case Some(tx) => transactions = transactions ++ tx.mc2scTransactionsOutputs().filter {
                _.isInstanceOf[ForwardTransfer]
              } map {
                _.asInstanceOf[ForwardTransfer]
              }
            }
          }
          ApiResponseUtil.toResponse(RespAllForwardTransfers(new ForwardTransfersView(transactions.asJava, true)))
        }
      }
    }
  }

  private def getBlockByEpochNumber(nodeView: AccountNodeView, epochNumber: Int): AccountBlock = {
    val accountHistory = nodeView.getNodeHistory
    val blockId = accountHistory.getBlockIdByHeight(epochNumber)
    if (blockId.isEmpty) return null
    val block = accountHistory.getBlockById(blockId.get())
    if (block.isEmpty) return null
    block.get()
  }
}

object AccountBlockRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetForwardTransfersRequests(epochNum: Int) {
    require(epochNum >= 0, "Epoch number must be positive")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllForwardTransfers(@JsonUnwrapped listOfFWT: ForwardTransfersView) extends SuccessResponse
}


object AccountBlockErrorResponse {
  case class ErrorNoBlockFound(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0401"
  }
}
