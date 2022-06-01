package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.horizen.SidechainTypes
import com.horizen.account.api.http.AccountTransactionErrorResponse.GenericTransactionError
import com.horizen.account.api.http.AccountTransactionRestScheme._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.node.{NodeStateBase, NodeWalletBase}
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class AccountTransactionApiRoute(override val settings: RESTApiSettings,
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
    NodeStateBase,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] with SidechainTypes {

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])

  override val route: Route = (pathPrefix("transaction")) {
    allTransactions ~ sendCoinsToAddress
  }

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
          ApiResponseUtil.toResponse(RespAllTransactionIds(unconfirmedTxs.asScala.toList.map(tx => tx.id.toString)))
        }
      }
    }
  }


  /**
   * Create and sign a core transaction, specifying regular outputs and fee. Search for and spend proper amount of regular coins. Then validate and send the transaction.
   * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
   */
  def sendCoinsToAddress: Route = (post & path("sendCoinsToAddress")) {
    entity(as[ReqSendCoinsToAddress]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
//        val outputList = body.outputs
//        val fee = body.fee
//        val wallet = sidechainNodeView.getNodeWallet
//        createCoreTransaction(outputList, fee.getOrElse(0L), wallet, sidechainNodeView)
        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.empty()))       }
//      match {
//        case Success(transaction) => validateAndSendTransaction(transaction)
//        case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
//      }
    }
  }


  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: (SidechainTypes#SCAT => SuccessResponse) = {
    transaction => TransactionIdDTO(transaction.id)
  }

//  private def createCoreTransaction(zenBoxDataList: List[TransactionOutput],
//                                    fee: Long,
//                                    wallet: AccountWallet,
//                                    sidechainNodeView: AccountNodeView): Try[SidechainCoreTransaction] = Try {
//
//    val memoryPool = sidechainNodeView.getNodeMemoryPool
//
//    val outputs: JList[BoxData[Proposition, Box[Proposition]]] = new JArrayList()
//    zenBoxDataList.foreach(element =>
//      outputs.add(new ZenBoxData(
//        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
//        element.value).asInstanceOf[BoxData[Proposition, Box[Proposition]]])
//    )
//
//
//    val outputsTotalAmount: Long = outputs.asScala.map(boxData => boxData.value()).sum
//    val inputsMinimumExpectedAmount: Long = outputsTotalAmount + fee
//    var inputsTotalAmount: Long = 0L
//
//    val boxes = ArrayBuffer[Box[Proposition]]()
//
//    if(inputsTotalAmount < inputsMinimumExpectedAmount)
//      throw new IllegalArgumentException("Not enough balances in the wallet to create transaction.")
//
//
//    // Create unsigned tx
//    val boxIds = boxes.map(_.id()).asJava
//    // Create a list of fake proofs for further messageToSign calculation
//    val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)
//    val unsignedTransaction = new SidechainCoreTransaction(boxIds, outputs, fakeProofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)
//
//    // Create signed tx.
//    val messageToSign = unsignedTransaction.messageToSign()
//    val proofs = boxes.map(box => {
//      wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
//    })
//
//    new SidechainCoreTransaction(boxIds, outputs, proofs.asJava, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)
//  }


//  private def validateAndSendTransaction(transaction: SidechainTypes#SCAT,
//                                         transactionResponseRepresentation: (SidechainTypes#SCAT => SuccessResponse) = defaultTransactionResponseRepresentation) = {
//    val barrier = Await.result(
//      sidechainTransactionActorRef ? BroadcastTransaction(transaction),
//      settings.timeout).asInstanceOf[Future[Unit]]
//    onComplete(barrier) {
//      case Success(_) =>
//        ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
//      case Failure(exp) =>
//        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exp))
//        )
//    }
//  }

}


object AccountTransactionRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllTransactions(format: Option[Boolean]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactions(transactions: List[SidechainTypes#SCAT]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactionIds(transactionIds: List[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqFindById(transactionId: String, blockHash: Option[String], transactionIndex: Option[Boolean], format: Option[Boolean])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionDTO(transaction: SidechainTypes#SCAT) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionBytesDTO(transactionBytes: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqDecodeTransactionBytes(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespDecodeTransactionBytes(transaction: SidechainTypes#SCAT) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionInput(boxId: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionOutput(publicKey: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionWithdrawalRequestOutput(mainchainAddress: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionForgerOutput(publicKey: String, blockSignPublicKey: Option[String], vrfPubKey: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateCoreTransaction(transactionInputs: List[TransactionInput],
                                                   regularOutputs: List[TransactionOutput],
                                                   withdrawalRequests: List[TransactionWithdrawalRequestOutput],
                                                   forgerOutputs: List[TransactionForgerOutput],
                                                   format: Option[Boolean]) {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(regularOutputs.nonEmpty || withdrawalRequests.nonEmpty || forgerOutputs.nonEmpty, "Empty outputs")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateCoreTransactionSimplified(regularOutputs: List[TransactionOutput],
                                                             withdrawalRequests: List[TransactionWithdrawalRequestOutput],
                                                             forgerOutputs: List[TransactionForgerOutput],
                                                             @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Long,
                                                             format: Option[Boolean]) {
    require(regularOutputs.nonEmpty || withdrawalRequests.nonEmpty || forgerOutputs.nonEmpty, "Empty outputs")
    require(fee >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSendCoinsToAddress(outputs: List[TransactionOutput],
                                                @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqWithdrawCoins(outputs: List[TransactionWithdrawalRequestOutput],
                                           @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateForgerStake(outputs: List[TransactionForgerOutput],
                                               @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSendTransactionPost(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionIdDTO(transactionId: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSpendForgingStake(transactionInputs: List[TransactionInput],
                                               regularOutputs: List[TransactionOutput],
                                               forgerOutputs: List[TransactionForgerOutput],
                                               format: Option[Boolean]) {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(regularOutputs.nonEmpty || forgerOutputs.nonEmpty, "Empty outputs")
  }



}

object AccountTransactionErrorResponse {

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