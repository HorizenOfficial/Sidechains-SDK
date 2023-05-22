package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.SidechainTypes
import io.horizen.account.api.http.route.AccountBlockRestSchema._
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.account.utils.AccountForwardTransfersHelper.getForwardTransfersForBlock
import io.horizen.account.utils.{AccountPayment, MainchainTxCrosschainOutputAddressUtil, ZenWeiConverter}
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.route.BlockBaseErrorResponse.ErrorInvalidBlockId
import io.horizen.api.http.route.BlockBaseRestSchema.ReqFeePayments
import io.horizen.api.http.route.{BlockBaseApiRoute, DisableApiRoute}
import io.horizen.api.http.{ApiResponseUtil, SuccessResponse}
import io.horizen.json.Views
import io.horizen.node.NodeWalletBase
import io.horizen.params.NetworkParams
import io.horizen.transaction.mainchain.ForwardTransfer
import sparkz.core.serialization.SparkzSerializer
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.ExecutionContext
import scala.jdk.OptionConverters.RichOptional

class AccountBlockApiRoute(
    override val settings: RESTApiSettings,
    override val sidechainNodeViewHolderRef: ActorRef,
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

  override def route: Route = pathPrefix("block") {
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

object AccountBlockApiRoute {
  def apply(settings: RESTApiSettings,
            sidechainNodeViewHolderRef: ActorRef,
            sidechainBlockActorRef: ActorRef,
            companion: SparkzSerializer[SidechainTypes#SCAT],
            forgerRef: ActorRef,
            params: NetworkParams
          )(implicit context: ActorRefFactory, ec: ExecutionContext): AccountBlockApiRoute = {
    if (params.isHandlingTransactionsEnabled)
      new AccountBlockApiRoute(settings, sidechainNodeViewHolderRef, sidechainBlockActorRef, companion, forgerRef, params)
    else
      new AccountBlockApiRoute(settings, sidechainNodeViewHolderRef, sidechainBlockActorRef, companion, forgerRef, params)
        with DisableApiRoute {

        override def listOfDisabledEndpoints: Seq[String] = Seq("startForging","stopForging", "generate")
        override val myPathPrefix: String = "block"
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
