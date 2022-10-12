package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.BlockBaseRestSchema.{ReqGenerateByEpochAndSlot, RespGenerate}
import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.api.http.BlockBaseErrorResponse.ErrorBlockNotCreated
import com.horizen.api.http.{ApiResponseUtil, BlockBaseApiRoute}
import com.horizen.api.http.JacksonSupport._
import com.horizen.block.SidechainBlockBase
import com.horizen.consensus.{intToConsensusEpochNumber, intToConsensusSlotNumber}
import com.horizen.forge.AbstractForger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.node.NodeWalletBase
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import sparkz.core.settings.RESTApiSettings
import scorex.util.ModifierId
import sparkz.core.serialization.SparkzSerializer
import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.horizen.account.api.http.AccountBlockRestSchema._
import com.horizen.account.api.http.SidechainBlockErrorResponse.ErrorNoBlockFound
import com.horizen.account.api.rpc.types.ForwardTransfersView
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import com.horizen.account.utils.AccountPayment
import com.horizen.api.http.{ErrorResponse, SuccessResponse}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionalGeneric

case class AccountBlockApiRoute(
                                  override val settings: RESTApiSettings,
                                  sidechainNodeViewHolderRef: ActorRef,
                                  sidechainBlockActorRef: ActorRef,
                                  companion: SparkzSerializer[SidechainTypes#SCAT],
                                  forgerRef: ActorRef)
                                 (implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends BlockBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] (settings, forgerRef){

  override val route: Route = pathPrefix("block") {
    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ findBlockInfoById ~ getFeePayments ~ getForwardTransfers ~ startForging ~ stopForging ~ generateBlockForEpochNumberAndSlot ~ getForgingInfo
  }

  def generateBlockForEpochNumberAndSlot: Route = (post & path("generate")) {
    entity(as[ReqGenerateByEpochAndSlot]) { body =>
      // TODO FOR MERGE checkit and possibly move to base class

      val forcedTx: Iterable[SidechainTypes#SCAT] = body.transactionsBytes
        .map(txBytes => companion.parseBytesTry(BytesUtils.fromHexString(txBytes)))
        .flatten(maybeTx => maybeTx.map(Seq(_)).getOrElse(None))

      val future = sidechainBlockActorRef ? TryForgeNextBlockForEpochAndSlot(intToConsensusEpochNumber(body.epochNumber), intToConsensusSlotNumber(body.slotNumber), forcedTx)
      val submitResultFuture = Await.result(future, timeout.duration).asInstanceOf[Future[Try[ModifierId]]]
      Try {
        Await.result(submitResultFuture, timeout.duration) match {
          case Success(id) =>
            ApiResponseUtil.toResponse(RespGenerate(id.asInstanceOf[String]))
          case Failure(e) =>
            ApiResponseUtil.toResponse(ErrorBlockNotCreated(s"Block was not created: ${e.getMessage}", JOptional.empty()))
        }
      } match {
        case Failure(e) =>
          // trap the error coming from block actor if any, just to avoid having a generic internal error at caller side
          ApiResponseUtil.toResponse(ErrorBlockNotCreated(s"Block processing failure: ${e.getMessage}", JOptional.empty()))
        case Success(response) =>
          response
      }
    }
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
        else ApiResponseUtil.toResponse(
          RespAllForwardTransfers(new ForwardTransfersView(
            getForwardTransfersForBlock(block.get()).asJava, true)
          )
        )
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
  private[api] case class ReqFeePayments(blockId: String) {
    require(blockId.length == SidechainBlockBase.BlockIdHexStringLength, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetForwardTransfersRequests(blockId: String) {
    require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllForwardTransfers(@JsonUnwrapped listOfFWT: ForwardTransfersView) extends SuccessResponse


  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespFeePayments(feePayments: Seq[AccountPayment]) extends SuccessResponse
}

object SidechainBlockErrorResponse {
  case class ErrorNoBlockFound(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0401"
  }
}
