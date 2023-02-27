package com.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainTypes
import com.horizen.account.api.http.route.AccountBlockRestSchema._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import com.horizen.account.utils.{AccountPayment, MainchainTxCrosschainOutputAddressUtil, ZenWeiConverter}
import com.horizen.api.http.route.BlockBaseErrorResponse.ErrorInvalidBlockId
import com.horizen.api.http.route.BlockBaseRestSchema.ReqFeePayments
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.route.BlockBaseApiRoute
import com.horizen.api.http.{ApiResponseUtil, SuccessResponse}
import com.horizen.json.Views
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.transaction.mainchain.ForwardTransfer
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.ExecutionContext
import scala.jdk.OptionConverters.RichOptional

case class AccountBlockApiRoute(
    override val settings: RESTApiSettings,
    sidechainNodeViewHolderRef: ActorRef,
    sidechainBlockActorRef: ActorRef,
    companion: SparkzSerializer[SidechainTypes#SCAT],
    forgerRef: ActorRef,
    params: NetworkParams
)(implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
    extends BlockBaseApiRoute[
      SidechainTypes#SCAT,
      AccountBlockHeader,
      AccountBlock,
      AccountFeePaymentsInfo,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView
    ](settings, sidechainBlockActorRef, companion, forgerRef, params) {

  override val route: Route = pathPrefix("block") {
    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ findBlockInfoById ~ getFeePayments ~
      getForwardTransfers ~ startForging ~ stopForging ~ generateBlockForEpochNumberAndSlot ~ getForgingInfo ~
      getCurrentHeight
  }

  private def parseForwardTransfer(tx: ForwardTransfer): ForwardTransferData = {
    val ftOutput = tx.getFtOutput
    val address = MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOutput.propositionBytes)
    val weiValue = ZenWeiConverter.convertZenniesToWei(ftOutput.amount)
    ForwardTransferData(address.toBytes, String.valueOf(weiValue))
  }

  /**
   * Return the list of forward transfers in a given block. Return error if specified block height does not exist.
   */
  def getForwardTransfers: Route = (post & path("getForwardTransfers")) {
    entity(as[ReqGetForwardTransfersRequests]) { body =>
      withNodeView { sidechainNodeView =>
        ApiResponseUtil.toResponse(
          sidechainNodeView.getNodeHistory.getBlockById(body.blockId).toScala
            .map(getForwardTransfersForBlock)
            .map(txs => txs.map(parseForwardTransfer))
            .map(RespAllForwardTransfers)
            .getOrElse(ErrorInvalidBlockId(s"Block with id: ${body.blockId} not found", JOptional.empty()))
        )
      }
    }
  }

  /**
   * Return the list of forgers fee payments paid after the given block was applied. Return empty list in case no fee
   * payments were paid.
   */
  def getFeePayments: Route = (post & path("getFeePayments")) {
    entity(as[ReqFeePayments]) { body =>
      applyOnNodeView { sidechainNodeView =>
        val payments = sidechainNodeView.getNodeHistory.getFeePaymentsInfo(body.blockId).toScala
          .map(_.payments).getOrElse(Seq())
        ApiResponseUtil.toResponse(RespFeePayments(payments))
      }
    }
  }
}

object AccountBlockRestSchema {

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqGetForwardTransfersRequests(blockId: String) {
    require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class ForwardTransferData(to: Array[Byte], value: String)

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespAllForwardTransfers(forwardTransfers: Seq[ForwardTransferData])
      extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespFeePayments(feePayments: Seq[AccountPayment]) extends SuccessResponse
}
