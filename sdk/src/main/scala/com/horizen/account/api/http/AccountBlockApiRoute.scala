package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.{JsonUnwrapped, JsonView}
import com.horizen.SidechainTypes
import com.horizen.account.api.http.AccountBlockRestSchema._
import com.horizen.account.api.http.SidechainBlockErrorResponse.ErrorNoBlockFound
import com.horizen.account.api.rpc.types.ForwardTransfersView
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.utils.AccountPayment
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.{ApiResponseUtil, BlockBaseApiRoute, ErrorResponse, SuccessResponse}
import com.horizen.node.NodeWalletBase
import com.horizen.serialization.Views
import com.horizen.transaction.mainchain.ForwardTransfer
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.ExecutionContext

case class AccountBlockApiRoute(override val settings: RESTApiSettings,
                                sidechainNodeViewHolderRef: ActorRef,
                                sidechainBlockActorRef: ActorRef, forgerRef: ActorRef)(implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends BlockBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView](settings, sidechainBlockActorRef: ActorRef, forgerRef: ActorRef) {

  override val route: Route = pathPrefix("block") {
    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ getFeePayments ~ getForwardTransfers ~ startForging ~ stopForging ~ generateBlockForEpochNumberAndSlot ~ getForgingInfo
  }

  /**
   * Return the list of forward transfers in a given block.
   * Return error if specified block height does not exist.
   */
  def getForwardTransfers: Route = (post & path("getForwardTransfers")) {
    entity(as[ReqGetForwardTransfersRequests]) { body =>
      withNodeView { sidechainNodeView =>
        val block = sidechainNodeView.getNodeHistory.getBlockById(body.blockId)
        if (block.isEmpty) ApiResponseUtil.toResponse(ErrorNoBlockFound("ErrorNoBlockFound", JOptional.empty()))
        else {
          var transactions: Seq[ForwardTransfer] = Seq()
          for (refDataWithFTs <- block.get().mainchainBlockReferencesData) {
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

  /**
   * Return the list of forgers fee payments paid after the given block was applied.
   * Return empty list in case no fee payments were paid.
   */
  def getFeePayments: Route = (post & path("getFeePayments")) {
    entity(as[ReqFeePayments]) { body =>
      applyOnNodeView { sidechainNodeView =>
        val sidechainHistory = sidechainNodeView.getNodeHistory
        val payments = sidechainHistory.getFeePaymentsInfo(body.blockId).asScala.map(_.payments).getOrElse(Seq())
        ApiResponseUtil.toResponse(RespFeePayments(payments))
      }
    }
  }
}


object AccountBlockRestSchema {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetForwardTransfersRequests(blockId: String) {
    require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllForwardTransfers(@JsonUnwrapped listOfFWT: ForwardTransfersView) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqFeePayments(blockId: String) {
    require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespFeePayments(feePayments: Seq[AccountPayment]) extends SuccessResponse
}

object SidechainBlockErrorResponse {
  case class ErrorNoBlockFound(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0401"
  }
}