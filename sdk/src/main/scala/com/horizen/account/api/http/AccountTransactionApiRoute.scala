package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.horizen.SidechainTypes
import com.horizen.account.api.http.AccountTransactionErrorResponse._
import com.horizen.account.api.http.AccountTransactionRestScheme._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.transaction.{EthereumTransaction, EthereumTransactionSerializer}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.transaction.Transaction
import org.web3j.crypto.{Keys, RawTransaction, Sign, SignedRawTransaction}
import scorex.core.settings.RESTApiSettings
import org.web3j.crypto.Sign.SignatureData
import com.horizen.account.wallet.AccountWallet
import com.horizen.utils.BytesUtils

import java.math.BigInteger
import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

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
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] with SidechainTypes {

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])


  override val route: Route = (pathPrefix("transaction")) {
    allTransactions ~ sendCoinsToAddress ~ createEIP1559Transaction ~ createLegacyTransaction ~ sendRawTransaction ~ signTransaction
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

  def getFittingSecret(nodeView: AccountNodeView, fromAddress: Option[String], txValueInWei: BigInteger)
  : Option[PrivateKeySecp256k1] = {
    val wallet = nodeView.getNodeWallet
    val allAccounts = wallet.secretsOfType(classOf[PrivateKeySecp256k1])
    val secret = allAccounts.find(
      a => (fromAddress.isEmpty ||
        BytesUtils.toHexString(a.asInstanceOf[PrivateKeySecp256k1].publicImage
          .address) == fromAddress.get) &&
        nodeView.getNodeState.getBalance(a.asInstanceOf[PrivateKeySecp256k1].publicImage.address)
          .getOrElse(BigInteger.valueOf(0)).compareTo(txValueInWei) >= 0// TODO account for gas
    )

    if (secret.nonEmpty) Option.apply(secret.asInstanceOf[PrivateKeySecp256k1])
    else Option.empty[PrivateKeySecp256k1]
  }

  def signTransactionWithSecret(secret: PrivateKeySecp256k1, tx: EthereumTransaction): EthereumTransaction = {
    val messageToSign = tx.messageToSign()
    val msgSignature = secret.sign(messageToSign)
    new EthereumTransaction(
      new SignedRawTransaction(
        tx.getTransaction.getTransaction,
        new SignatureData(msgSignature.getV, msgSignature.getR, msgSignature.getV)
      )
    )
  }

  /**
   * Create and sign a core transaction, specifying regular outputs and fee. Search for and spend proper amount of regular coins. Then validate and send the transaction.
   * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
   */
  def sendCoinsToAddress: Route = (post & path("sendCoinsToAddress")) {
    entity(as[ReqSendCoinsToAddress]) { body =>
      // lock the view and try to create EvmTransaction
      // TODO also account for gas fees
      applyOnNodeView { sidechainNodeView =>
        val valueInWei = ZenWeiConverter.convertZenniesToWei(body.value)
        val destAddress = body.to
        val gasPrice = BigInteger.valueOf(1) // TODO actual gas implementation
        val gasLimit = BigInteger.valueOf(1) // TODO actual gas implementation
        // check if the fromAddress is either empty or it fits and the value is high enough
        val secret = getFittingSecret(sidechainNodeView, body.from, valueInWei)
        secret match {
          case Some(secret) =>
            val nonce = BigInteger.valueOf(
              sidechainNodeView.getNodeState.getAccount(secret.publicImage.address).nonce)
            val tmpTx = new EthereumTransaction(
              destAddress,
              nonce,
              gasPrice,
              gasLimit,
              valueInWei,
              "",
              null
            )
            validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
          case None =>
            ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
        }
      }
    }
  }

  /**
   * Create and sign a core transaction, specifying regular outputs and fee. Search for and spend proper amount of regular coins. Then validate and send the transaction.
   * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
   */
  def createEIP1559Transaction: Route = (post & path("createEIP1559Transaction")) {
    entity(as[ReqEIP1559Transaction]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        var signedTx: EthereumTransaction = new EthereumTransaction(
          body.chainId,
          body.to.orNull,
          body.nonce,
          body.gasLimit,
          body.maxPriorityFeePerGas,
          body.maxFeePerGas,
          body.value,
          body.data,
          if (body.signature_v.isDefined)
            new SignatureData(
              body.signature_v.get,
              body.signature_r.get,
              body.signature_s.get)
          else
            null
        );
        if (!signedTx.isSigned) {
          val secret =
            getFittingSecret(sidechainNodeView, body.from, signedTx.getValue)
          secret match {
            case Some(secret) =>
              signedTx = signTransactionWithSecret(secret, signedTx)
            case None =>
              return ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
          }
        }
        validateAndSendTransaction(signedTx)
      }
    }
  }

  /**
   * Create a legacy evm transaction, specifying inputs.
   */
  def createLegacyTransaction: Route = (post & path("createLegacyTransaction")) {
    entity(as[ReqLegacyTransaction]) { body =>
      // lock the view and try to send the tx
      applyOnNodeView { sidechainNodeView =>
        var signedTx = new EthereumTransaction(
          body.to.orNull,
          body.nonce,
          body.gasPrice,
          body.gasLimit,
          body.value,
          body.data,
          if (body.signature_v.isDefined)
            new SignatureData(
              body.signature_v.get,
              body.signature_r.get,
              body.signature_s.get)
          else
            null
        )
        if (!signedTx.isSigned) {
          val secret =
            getFittingSecret(sidechainNodeView, body.from, signedTx.getValue)
          secret match {
            case Some(secret) =>
              signedTx = signTransactionWithSecret(secret, signedTx)
            case None =>
              return ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
          }
        }
        validateAndSendTransaction(signedTx)
      }
    }
  }

  /**
   * Create a raw evm transaction, specifying the bytes.
   */
  def sendRawTransaction: Route = (post & path("createRawTransaction")) {
    entity(as[ReqRawTransaction]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        var signedTx = EthereumTransactionSerializer.getSerializer.parseBytes(body.payload)
        if (!signedTx.isSigned) {
          val secret =
            getFittingSecret(sidechainNodeView, body.from, signedTx.getValue)
          secret match {
            case Some(secret) =>
              signedTx = signTransactionWithSecret(secret, signedTx)
            case None =>
              return ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
          }
        }
        validateAndSendTransaction(signedTx)
      }
    }
  }

  def signTransaction: Route = (post & path("signTransaction")) {
    entity(as[ReqRawTransaction]) {
      body => {
        applyOnNodeView { sidechainNodeView =>
          var signedTx = EthereumTransactionSerializer.getSerializer.parseBytes(body.payload)
          val secret =
            getFittingSecret(sidechainNodeView, body.from, signedTx.getValue)
          secret match {
            case Some(secret) =>
              signedTx = signTransactionWithSecret(secret, signedTx)
            case None =>
              return ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
          }
          ApiResponseUtil.toResponse(defaultTransactionResponseRepresentation(signedTx))
        }
      }
    }
  }


  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: (Transaction => SuccessResponse) = {
    transaction => TransactionIdDTO(transaction.id)
  }


  private def validateAndSendTransaction(transaction: Transaction,
                                         transactionResponseRepresentation: (Transaction => SuccessResponse) = defaultTransactionResponseRepresentation) = {

    val barrier = Await.result(
      sidechainTransactionActorRef ? BroadcastTransaction(transaction),
      settings.timeout).asInstanceOf[Future[Unit]]
    onComplete(barrier) {
      case Success(_) =>
        ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exp) =>
        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exp))
        )
    }
  }

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
  private[api] case class ReqSendCoinsToAddress(from: Option[String],
                                                to: String,
                                                @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long) {
    require(to.nonEmpty, "Empty destination address")
    require(value >= 0, "Negative value. Value must be >= 0")
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

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqEIP1559Transaction(chainId: Long,
                                                from: Option[String],
                                                to: Option[String],
                                                nonce: BigInteger,
                                                gasLimit: BigInteger,
                                                maxPriorityFeePerGas: BigInteger,
                                                maxFeePerGas: BigInteger,
                                                value: BigInteger,
                                                data: String,
                                                signature_v: Option[Array[Byte]],
                                                signature_r: Option[Array[Byte]],
                                                signature_s: Option[Array[Byte]]) {
    require(
      (signature_v.nonEmpty && signature_r.nonEmpty && signature_s.nonEmpty)
        || (signature_v.isEmpty && signature_r.isEmpty && signature_s.isEmpty),
      "Signature can not be partial"
    )
    require(gasLimit.signum() > 0, "Gas limit can not be 0")
    require(chainId > 0, "ChainId must be positive")
    require(maxPriorityFeePerGas.signum() > 0, "MaxPriorityFeePerGas must be greater than 0")
    require(maxFeePerGas.signum() > 0, "MaxFeePerGas must be greater than 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqLegacyTransaction(to: Option[String],
                                               from: Option[String],
                                               nonce: BigInteger,
                                               gasLimit: BigInteger,
                                               gasPrice: BigInteger,
                                               value: BigInteger,
                                               data: String,
                                               signature_v: Option[Array[Byte]],
                                               signature_r: Option[Array[Byte]],
                                               signature_s: Option[Array[Byte]]) {
    require(
      (signature_v.nonEmpty && signature_r.nonEmpty && signature_s.nonEmpty)
        || (signature_v.isEmpty && signature_r.isEmpty && signature_s.isEmpty),
      "Signature can not be partial"
    )
    require(gasLimit.signum() > 0, "Gas limit can not be 0")
    require(gasPrice.signum() > 0, "Gas price can not be 0")
    require(to.isEmpty || to.get.length == 42 /* address length with prefix 0x */)
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqRawTransaction(from: Option[String], payload: Array[Byte]);


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

  case class ErrorInsufficientBalance(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0301"
  }

}