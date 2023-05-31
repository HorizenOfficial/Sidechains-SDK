package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.SidechainNodeViewBase
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import io.horizen.api.http.route.TransactionBaseErrorResponse._
import io.horizen.api.http.route.TransactionBaseRestScheme._
import io.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse}
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.consensus.ForgingStakeInfo
import io.horizen.json.Views
import io.horizen.node._
import io.horizen.transaction._
import io.horizen.utils.BytesUtils
import sparkz.core.serialization.SparkzSerializer

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
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
        case Failure(exception) =>
          ApiResponseUtil.toResponse(ErrorByteTransactionParsing("ErrorByteTransactionParsing", JOptional.of(exception)))
      }
    }
  }

  /**
   * Validate and send a transaction, given its serialization as input.
   * Return error in case of invalid transaction or parsing error, otherwise return the id of the transaction.
   */
  def sendTransaction: Route = (post & path("sendTransaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqSendTransaction]) { body =>
          val transactionBytes = BytesUtils.fromHexString(body.transactionBytes)
          companion.parseBytesTry(transactionBytes) match {
            case Success(transaction) =>
              validateAndSendTransaction(transaction)
            case Failure(exception) =>
              ApiResponseUtil.toResponse(ErrorByteTransactionParsing("ErrorByteTransactionParsing", JOptional.of(exception)))
          }
        }
      }
    }
  }

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: TX => SuccessResponse = {
    transaction => TransactionIdDTO(transaction.id)
  }

  val rawTransactionResponseRepresentation: TX => SuccessResponse = {
    transaction =>
      TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(transaction)))
  }

  protected def validateAndSendTransaction(transaction: TX,
                                         transactionResponseRepresentation: TX => SuccessResponse = defaultTransactionResponseRepresentation): Route = {

    val barrier = Await.result(
      sidechainTransactionActorRef ? BroadcastTransaction(transaction),
      60.minutes).asInstanceOf[Future[Unit]]
    onComplete(barrier) {
      case Success(_) =>
        ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exception) =>
        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exception)))
    }

  }
}


object TransactionBaseRestScheme {

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqAllTransactions(format: Option[Boolean]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespAllTransactions[TX](transactions: List[TX]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespAllTransactionIds(transactionIds: List[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class TransactionDTO[TX](transaction: TX) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class TransactionIdDTO(transactionId: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class TransactionBytesDTO(transactionBytes: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqDecodeTransactionBytes(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  case class ReqSendTransaction(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespDecodeTransactionBytes[TX](transaction: TX) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespAllForgingStakesInfo(stakes: List[ForgingStakeInfo]) extends SuccessResponse
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

  case class ErrorBadCircuit(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0404"
  }

}
