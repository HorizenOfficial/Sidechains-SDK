package com.horizen.api.http

import java.lang.Byte
import java.util.function.Consumer
import java.{lang, util}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.box.{Box, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.{NodeWallet, SidechainNodeView}
import com.horizen.proposition._
import com.horizen.secret.PrivateKey25519
import com.horizen.{SidechainTypes, transaction}
import com.horizen.transaction._
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.core.settings.RESTApiSettings
import com.horizen.utils.Pair

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import JacksonSupport._
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.horizen.api.http.SidechainTransactionErrorResponse._
import com.horizen.api.http.SidechainTransactionRestScheme._
import com.horizen.serialization.Views


case class SidechainTransactionApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute {

  override val route: Route = (pathPrefix("transaction")) {
    allTransactions ~ findById ~ decodeTransactionBytes ~ createRegularTransaction ~ createRegularTransactionSimplified ~
    sendCoinsToAddress ~ sendTransaction ~ withdrawCoins
  }

  private var companion: SidechainTransactionsCompanion = new SidechainTransactionsCompanion(new util.HashMap[Byte, TransactionSerializer[SidechainTypes#SCBT]]())

  /**
    * Returns an array of transaction ids if formatMemPool=false, otherwise a JSONObject for each transaction.
    */
  def allTransactions: Route = (post & path("allTransactions")) {
    entity(as[ReqAllTransactions]) { body =>
      withNodeView { sidechainNodeView =>
        var unconfirmedTxs = sidechainNodeView.getNodeMemoryPool.getTransactions()
        if (body.format.getOrElse(true)) {
          ApiResponseUtil.toResponse(RespAllTransactions(unconfirmedTxs.asScala.toList))
        } else {
          ApiResponseUtil.toResponse(RespAllTransactionIds(unconfirmedTxs.asScala.toList.map(tx => tx.id.toString)))
        }
      }
    }
  }

  /**
    * Follows the same behaviour as the corresponding RPC call in zend: by default it will look for
    * transaction in memory pool. Additional parameters are:
    * -format: if true a JSON representation of transaction is returned, otherwise return the transaction serialized as
    * a hex string. If format is not specified, false behaviour is assumed as default;
    * -blockHash: If specified, will look for tx in the corresponding block
    * -txIndex: If specified will look for transaction in all blockchain blocks;
    *
    * All the possible behaviours are be:
    * 1) blockHash set -> Search in block referenced by blockHash (do not care about txIndex parameter)
    * 2) blockHash not set, txIndex = true -> Search in memory pool, if not found, search in the whole blockchain
    * 3) blockHash not set, txIndex = false -> Search in memory pool
    */
  def findById: Route = (post & path("findById")) {
    entity(as[ReqFindById]) { body =>
      withNodeView { sidechainNodeView =>
        val memoryPool = sidechainNodeView.getNodeMemoryPool
        val history = sidechainNodeView.getNodeHistory

        def searchTransactionInMemoryPool(id: String): Option[_ <: Transaction] = {
          var opt = memoryPool.getTransactionById(id)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        def searchTransactionInBlock(id: String, blockHash: String): Option[_ <: Transaction] = {
          var opt = history.searchTransactionInsideSidechainBlock(id, blockHash)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        def searchTransactionInBlockchain(id: String): Option[_ <: Transaction] = {
          var opt = history.searchTransactionInsideBlockchain(id)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        var txId = body.transactionId
        var format = body.format.getOrElse(false)
        var blockHash = body.blockHash.getOrElse("")
        var txIndex = body.transactionIndex.getOrElse(false)
        var transaction: Option[Transaction] = None
        var error: String = ""


        // Case --> blockHash not set, txIndex = true -> Search in memory pool, if not found, search in the whole blockchain
        if (blockHash.isEmpty && txIndex) {
          // Search first in memory pool
          transaction = searchTransactionInMemoryPool(txId)
          // If not found search in the whole blockchain
          if (transaction.isEmpty)
            transaction = searchTransactionInBlockchain(txId)
          if (transaction.isEmpty)
            error = s"Transaction $txId not found in memory pool and blockchain"
        }

        // Case --> blockHash not set, txIndex = false -> Search in memory pool
        else if (blockHash.isEmpty && !txIndex) {
          // Search in memory pool
          transaction = searchTransactionInMemoryPool(txId)
          if (transaction.isEmpty)
            error = s"Transaction $txId not found in memory pool"
        }

        // Case --> blockHash set -> Search in block referenced by blockHash (do not care about txIndex parameter)
        else if (!blockHash.isEmpty) {
          transaction = searchTransactionInBlock(txId, blockHash)
          if (transaction.isEmpty)
            error = s"Transaction $txId not found in specified block"
        }

        transaction match {
          case Some(t) =>
            if (format) {
              //TO-DO JSON representation of transaction
              ApiResponseUtil.toResponse(TransactionDTO(t))
            } else {
              ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(t))))
            }
          case None =>
            // TO-DO Change the errorCode
            ApiResponseUtil.toResponse(ErrorNotFoundTransactionId(error, None))
        }
      }
    }
  }

  /**
    * Return a JSON representation of a transaction given its byte serialization.
    */
  def decodeTransactionBytes: Route = (post & path("decodeTransactionBytes")) {
    entity(as[ReqDecodeTransactionBytes]) { body =>
      withNodeView { sidechainNodeView =>
        var tryTX = companion.parseBytesTry(BytesUtils.fromHexString(body.transactionBytes))
        tryTX match {
          case Success(tx) =>
            //TO-DO JSON representation of transaction
            ApiResponseUtil.toResponse(RespDecodeTransactionBytes(tx))
          case Failure(exp) =>
            ApiResponseUtil.toResponse(ErrorByteTransactionParsing(exp.getMessage, Some(exp)))
        }
      }
    }
  }

  /**
    * Create and sign a regular transaction, specifying inputs and outputs.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createRegularTransaction: Route = (post & path("createRegularTransaction")) {
    entity(as[ReqCreateRegularTransaction]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val inputBoxes = wallet.allBoxes().asScala
          .filter(box => body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

        if (inputBoxes.length < body.transactionInputs.size) {
          ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput(s"Unable to find input(s)", None))
        } else {
          var inSum: Long = 0
          var outSum: Long = 0

          val inputs: IndexedSeq[Pair[RegularBox, PrivateKey25519]] = inputBoxes.map(box => {
            var secret = wallet.secretByPublicKey(box.proposition())
            var privateKey = secret.get().asInstanceOf[PrivateKey25519]
            wallet.secretByPublicKey(box.proposition()).get().asInstanceOf[PrivateKey25519]
            new Pair(
              box.asInstanceOf[RegularBox],
              privateKey)
          }
          ).toIndexedSeq

          val outputs: IndexedSeq[Pair[PublicKey25519Proposition, lang.Long]] = body.transactionOutputs.map(element =>
            new Pair(
              PublicKey25519PropositionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value))
          ).toIndexedSeq

          val withdrawalRequests: IndexedSeq[Pair[MCPublicKeyHashProposition, lang.Long]] = body.withdrawalRequests.map(element =>
            new Pair(
              MCPublicKeyHashPropositionSerializer.getSerializer().parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value))
          ).toIndexedSeq

          inputs.foreach(pair => inSum += pair.getKey.value())
          outputs.foreach(pair => outSum += pair.getValue)

          val fee: Long = inSum - outSum

          try {
            val regularTransaction = RegularTransaction.create(
              JavaConverters.seqAsJavaList(inputs),
              JavaConverters.seqAsJavaList(outputs),
              JavaConverters.seqAsJavaList(withdrawalRequests),
              fee, System.currentTimeMillis())

            if (body.format.getOrElse(false))
              ApiResponseUtil.toResponse(TransactionDTO(regularTransaction))
            else
              ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(regularTransaction))))
          } catch {
            case t: Throwable =>
              ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
          }
        }
      }
    }
  }

  /**
    * Create and sign a regular transaction, specifying outputs and fee.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createRegularTransactionSimplified: Route = (post & path("createRegularTransactionSimplified")) {
    entity(as[ReqCreateRegularTransactionSimplified]) { body =>
      withNodeView { sidechainNodeView =>
        var outputList = body.transactionOutputs
        var withdrawalRequestList = body.withdrawalRequests
        var fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        try {
          var regularTransaction = createRegularTransactionSimplified_(outputList, withdrawalRequestList, fee, wallet, sidechainNodeView)

          if (body.format.getOrElse(false))
            ApiResponseUtil.toResponse(TransactionDTO(regularTransaction))
          else
            ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(regularTransaction))))
        } catch {
          case t: Throwable =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
        }
      }
    }
  }

  // Note: method should return Try[RegularTransaction]
  private def createRegularTransactionSimplified_(outputList: List[TransactionOutput],
                                                  withdrawalRequestList: List[TransactionOutput],
                                                  fee: Long, wallet: NodeWallet,
                                                  sidechainNodeView: SidechainNodeView): RegularTransaction = {

    val memoryPool = sidechainNodeView.getNodeMemoryPool
    val boxIdsToExclude: ArrayBuffer[scala.Array[scala.Byte]] = ArrayBuffer[scala.Array[scala.Byte]]()

    memoryPool.getTransactionsSortedByFee(memoryPool.getSize).forEach(new Consumer[transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]] {
      override def accept(t: transaction.BoxTransaction[_ <: Proposition, _ <: Box[_ <: Proposition]]): Unit = {
        t.boxIdsToOpen().forEach(new Consumer[ByteArrayWrapper] {
          override def accept(t: ByteArrayWrapper): Unit = {
            boxIdsToExclude += t.data
          }
        })
      }
    })

    var outputs: java.util.List[Pair[PublicKey25519Proposition, lang.Long]] = outputList.map(element =>
      new Pair(new PublicKey25519Proposition(BytesUtils.fromHexString(element.publicKey)), new lang.Long(element.value))).asJava

    var withdrawalRequests: java.util.List[Pair[MCPublicKeyHashProposition, lang.Long]] = withdrawalRequestList.map(element =>
      new Pair(new MCPublicKeyHashProposition(BytesUtils.fromHexString(element.publicKey)), new lang.Long(element.value))).asJava

    var tx: RegularTransaction = null
    try {
      tx = RegularTransactionCreator.create(wallet, outputs, withdrawalRequests,
        wallet.allSecrets().get(0).asInstanceOf[PrivateKey25519].publicImage(), fee, boxIdsToExclude.asJava)
    }
    catch {
      case e: Exception => log.error(s"RegularTransaction creaion error: ${e.getMessage}")
        throw e
    }
    tx // to do: see note above
  }

  /**
    * Create and sign a regular transaction, specifying outputs and fee. Then validate and send the transaction.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def sendCoinsToAddress: Route = (post & path("sendCoinsToAddress")) {
    entity(as[ReqSendCoinsToAddress]) { body =>
      withNodeView { sidechainNodeView =>
        val outputList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        try {
          val regularTransaction = createRegularTransactionSimplified_(outputList, List(),
            fee.getOrElse(0L), wallet, sidechainNodeView)
          validateAndSendTransaction(regularTransaction)
        } catch {
          case t: Throwable =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
        }
      }
    }
  }

  def withdrawCoins: Route = (post & path("withdrawCoins")) {
    entity(as[ReqWithdrawCoins]) { body =>
      withNodeView { sidechainNodeView =>
        val withdrawalOutputsList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        try {
          val regularTransaction = createRegularTransactionSimplified_(List(), withdrawalOutputsList,
            fee.getOrElse(0L), wallet, sidechainNodeView)
          validateAndSendTransaction(regularTransaction)
        } catch {
          case t: Throwable =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
        }
      }
    }
  }

  private def validateAndSendTransaction(transaction: Transaction) = {
    withNodeView {
      sidechainNodeView =>
        val barrier = Await.result(
          sidechainTransactionActorRef ? BroadcastTransaction(transaction),
          settings.timeout).asInstanceOf[Future[Unit]]
        onComplete(barrier) {
          case Success(result) =>
            ApiResponseUtil.toResponse(TransactionIdDTO(transaction.id))
          case Failure(exp) =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(exp))
            )
        }
    }
  }

  /**
    * Validate and send a transaction, given its serialization as input.
    * Return error in case of invalid transaction or parsing error, otherwise return the id of the transaction.
    */
  def sendTransaction: Route = (post & path("sendTransaction")) {
    entity(as[ReqSendTransactionPost]) { body =>
      withNodeView { sidechainNodeView =>
        var transactionBytes = BytesUtils.fromHexString(body.transactionBytes)
        companion.parseBytesTry(transactionBytes) match {
          case Success(transaction) =>
            validateAndSendTransaction(transaction)
          case Failure(exception) =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(exception)))
        }
      }
    }
  }

}


object SidechainTransactionRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllTransactions(format: Option[Boolean]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactions(transactions: List[Transaction]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactionIds(transactionIds: List[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqFindById(transactionId: String, blockHash: Option[String], transactionIndex: Option[Boolean], format: Option[Boolean])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionDTO(transaction: Transaction) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionBytesDTO(transactionBytes: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqDecodeTransactionBytes(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespDecodeTransactionBytes(transaction: Transaction) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionInput(boxId: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionOutput(publicKey: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateRegularTransaction(transactionInputs: List[TransactionInput],
                                                      transactionOutputs: List[TransactionOutput],
                                                      withdrawalRequests: List[TransactionOutput],
                                                      format: Option[Boolean]) {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(transactionOutputs.nonEmpty, "Empty outputs list")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateRegularTransactionSimplified(transactionOutputs: List[TransactionOutput],
                                                                withdrawalRequests: List[TransactionOutput],
                                                                @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Long,
                                                                format: Option[Boolean]) {
    require(transactionOutputs.nonEmpty, "Empty outputs list")
    require(fee >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSendCoinsToAddress(outputs: List[TransactionOutput],
                                                @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqWithdrawCoins(outputs: List[TransactionOutput],
                                           @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSendTransactionPost(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionIdDTO(transactionId: String) extends SuccessResponse

}

object SidechainTransactionErrorResponse {

  case class ErrorNotFoundTransactionId(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0201"
  }

  case class ErrorNotFoundTransactionInput(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0202"
  }

  case class ErrorByteTransactionParsing(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0203"
  }

  case class GenericTransactionError(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0204"
  }

}