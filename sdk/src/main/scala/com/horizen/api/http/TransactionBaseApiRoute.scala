package com.horizen.api.http


import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainNodeViewBase
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.TransactionBaseErrorResponse._
import com.horizen.api.http.TransactionBaseRestScheme._
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.node._
import com.horizen.serialization.Views
import com.horizen.transaction._
import com.horizen.utils.BytesUtils

import java.util.{Optional => JOptional}
import sparkz.core.serialization.SparkzSerializer

import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class TransactionBaseApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](
                                  sidechainTransactionActorRef: ActorRef,
                                  companion: SparkzSerializer[TX])
                                 (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute[TX, H, PM, FPI, NH, NS, NW, NP, NV] {

  /**
    * Returns an array of transaction ids if formatMemPool=false, otherwise a JSONObject for each transaction.
    */
  def allTransactions: Route = (post & path("allTransactions")) {
    entity(as[ReqAllTransactions]) { body =>
      withNodeView { sidechainNodeView =>
        val unconfirmedTxs = sidechainNodeView.getNodeMemoryPool.getTransactions()
        if (body.format.getOrElse(true)) {
          ApiResponseUtil.toResponse(RespAllTransactions(unconfirmedTxs.asScala.toList))
        } else {
          ApiResponseUtil.toResponse(RespAllTransactionIds(unconfirmedTxs.asScala.toList.map(_.id)))
        }
      }
    }
  }

  /**
    * Return a JSON representation of a transaction given its byte serialization.
    */
  def decodeTransactionBytes: Route = (post & path("decodeTransactionBytes")) {
    entity(as[ReqDecodeTransactionBytes]) { body =>
      companion.parseBytesTry(BytesUtils.fromHexString(body.transactionBytes)) match {
        case Success(tx) =>
          //TO-DO JSON representation of transaction
          ApiResponseUtil.toResponse(RespDecodeTransactionBytes(tx))
        case Failure(exp) =>
          ApiResponseUtil.toResponse(ErrorByteTransactionParsing(exp.getMessage, JOptional.of(exp)))
      }
    }
  }

  def allActiveForgingStakeInfo: Route = (post & path("allActiveForgingStakeInfo")) {
    withNodeView { sidechainNodeView =>
      val nodeState = sidechainNodeView.getNodeState
      val listOfForgerStakes = nodeState.getOrderedForgingStakesInfoSeq
      ApiResponseUtil.toResponse(RespAllForgingStakesInfo(listOfForgerStakes.toList))
    }
  }

  def myActiveForgingStakeInfo: Route = (post & path("myActiveForgingStakeInfo")) {
    withAuth {
      withNodeView { sidechainNodeView =>
        val nodeState = sidechainNodeView.getNodeState
        val listOfForgerStakes = nodeState.getOrderedForgingStakesInfoSeq

        if (listOfForgerStakes.nonEmpty) {
          val wallet = sidechainNodeView.getNodeWallet
          val walletPubKeys = wallet.allSecrets().map(_.publicImage).toSeq
          val signingStakes = listOfForgerStakes.view.filter(stake => {
            walletPubKeys.contains(stake.blockSignPublicKey) &&
              walletPubKeys.contains(stake.vrfPublicKey)
          })
          ApiResponseUtil.toResponse(RespAllForgingStakesInfo(signingStakes.toList))
        } else {
          ApiResponseUtil.toResponse(RespAllForgingStakesInfo(Seq().toList))
        }
      }
    }
  }

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: TX => SuccessResponse = {
    transaction => TransactionIdDTO(transaction.id)
  }

  protected def validateAndSendTransaction(transaction: TX,
                                         transactionResponseRepresentation: TX => SuccessResponse = defaultTransactionResponseRepresentation): Route = {

    val barrier = Await.result(
      sidechainTransactionActorRef ? BroadcastTransaction(transaction),
      settings.timeout).asInstanceOf[Future[Unit]]
    onComplete(barrier) {
      case Success(_) =>
        ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exp) =>
        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exp)))
    }

  }
}


object TransactionBaseRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllTransactions(format: Option[Boolean]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactions[TX](transactions: List[TX]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactionIds(transactionIds: List[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionDTO[TX](transaction: TX) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionBytesDTO(transactionBytes: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqDecodeTransactionBytes(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespDecodeTransactionBytes[TX](transaction: TX) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllForgingStakesInfo(stakes: List[ForgingStakeInfo]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionIdDTO(transactionId: String) extends SuccessResponse
}

object TransactionBaseErrorResponse {

  case class ErrorNotFoundTransactionId(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0201"
  }

  case class ErrorNotFoundTransactionInput(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0202"
  }

  case class ErrorByteTransactionParsing(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0203"
  }

  case class GenericTransactionError(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0204"
  }

}