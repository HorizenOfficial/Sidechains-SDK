package com.horizen.api.http

import java.lang.Byte
import java.{lang, util}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.box.{ForgerBox, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.{NodeWallet, SidechainNodeView}
import com.horizen.proposition._
import com.horizen.secret.PrivateKey25519
import com.horizen.SidechainTypes
import com.horizen.transaction._
import com.horizen.utils.BytesUtils
import scorex.core.settings.RESTApiSettings
import com.horizen.utils.Pair

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import JacksonSupport._
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.horizen.api.http.SidechainTransactionErrorResponse._
import com.horizen.api.http.SidechainTransactionRestScheme._
import com.horizen.box.data.{BoxData, ForgerBoxData, RegularBoxData, WithdrawalRequestBoxData}
import com.horizen.serialization.Views
import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.vrf.VRFPublicKey

case class SidechainTransactionApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute with SidechainTypes {

  override val route: Route = (pathPrefix("transaction")) {
    allTransactions ~ findById ~ decodeTransactionBytes ~ createRegularTransaction ~ createRegularTransactionSimplified ~
    sendCoinsToAddress ~ sendTransaction ~ withdrawCoins ~ makeForgerStake ~ spendForgingStake
  }

  private var companion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new util.HashMap[Byte, TransactionSerializer[SidechainTypes#SCBT]]())

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

        def searchTransactionInMemoryPool(id: String): Option[SidechainTypes#SCBT] = {
          var opt = memoryPool.getTransactionById(id)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        def searchTransactionInBlock(id: String, blockHash: String): Option[SidechainTypes#SCBT] = {
          var opt = history.searchTransactionInsideSidechainBlock(id, blockHash)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        def searchTransactionInBlockchain(id: String): Option[SidechainTypes#SCBT] = {
          var opt = history.searchTransactionInsideBlockchain(id)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        var txId = body.transactionId
        var format = body.format.getOrElse(false)
        var blockHash = body.blockHash.getOrElse("")
        var txIndex = body.transactionIndex.getOrElse(false)
        var transaction: Option[SidechainTypes#SCBT] = None
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

          val inputs: JList[Pair[RegularBox, PrivateKey25519]] = new JArrayList()
          inputBoxes.foreach(box => {
            var secret = wallet.secretByPublicKey(box.proposition())
            var privateKey = secret.get().asInstanceOf[PrivateKey25519]
            inputs.add(new Pair(box.asInstanceOf[RegularBox], privateKey))
          })

          val outputs: JList[BoxData[_ <: Proposition]] = new JArrayList()
          body.regularOutputs.foreach(element =>
            outputs.add(new RegularBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value))
            )
          )
          body.withdrawalRequests.foreach(element =>
            outputs.add(new WithdrawalRequestBoxData(
              MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value))
            )
          )
          body.forgerOutputs.foreach(element =>
            outputs.add(new ForgerBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value),
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.rewardKey.getOrElse(element.publicKey))),
              new VRFPublicKey(BytesUtils.fromHexString(element.vrfPubKey)))  // TODO: replace with VRFPublicKeySerializer later
            )
          )

          inputs.forEach(pair => inSum += pair.getKey.value())
          outputs.forEach(boxData => outSum += boxData.value())
          val fee: Long = inSum - outSum

          try {
            val regularTransaction = RegularTransaction.create(
              inputs,
              outputs,
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
        var outputList = body.regularOutputs
        var withdrawalRequestList = body.withdrawalRequests
        var forgerOutputList = body.forgerOutputs
        var fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        try {
          var regularTransaction = createRegularTransactionSimplified_(outputList, withdrawalRequestList, forgerOutputList, fee, wallet, sidechainNodeView)

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
                                                  forgerOutputsList: List[TransactionForgerOutput],
                                                  fee: Long, wallet: NodeWallet,
                                                  sidechainNodeView: SidechainNodeView): RegularTransaction = {

    val memoryPool = sidechainNodeView.getNodeMemoryPool
    val boxIdsToExclude: JArrayList[Array[scala.Byte]] = new JArrayList()

    for(transaction <- memoryPool.getTransactionsSortedByFee(memoryPool.getSize).asScala)
      for(id <- transaction.boxIdsToOpen().asScala)
        boxIdsToExclude.add(id.data)

    val outputs: JList[BoxData[_ <: Proposition]] = new JArrayList()
    outputList.foreach(element =>
      outputs.add(new RegularBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
        new lang.Long(element.value)))
    )
    withdrawalRequestList.foreach(element =>
      outputs.add(new WithdrawalRequestBoxData(
        MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
        new lang.Long(element.value)))
    )
    forgerOutputsList.foreach(element =>
      outputs.add(new ForgerBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
        new lang.Long(element.value),
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.rewardKey.getOrElse(element.publicKey))),
        new VRFPublicKey(BytesUtils.fromHexString(element.vrfPubKey)))  // TODO: replace with VRFPublicKeySerializer later
      )
    )

    var tx: RegularTransaction = null
    try {
      tx = RegularTransactionCreator.create(wallet, outputs,
        wallet.allSecrets().get(0).asInstanceOf[PrivateKey25519].publicImage(), fee, boxIdsToExclude)
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
          val regularTransaction = createRegularTransactionSimplified_(outputList, List(), List(),
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
          val regularTransaction = createRegularTransactionSimplified_(List(), withdrawalOutputsList, List(),
            fee.getOrElse(0L), wallet, sidechainNodeView)
          validateAndSendTransaction(regularTransaction)
        } catch {
          case t: Throwable =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
        }
      }
    }
  }

  def makeForgerStake: Route = (post & path("makeForgerStake")) {
    entity(as[ReqCreateForgerCoins]) { body =>
      withNodeView { sidechainNodeView =>
        val forgerOutputsList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        try {
          val regularTransaction = createRegularTransactionSimplified_(List(), List(), forgerOutputsList,
            fee.getOrElse(0L), wallet, sidechainNodeView)
          validateAndSendTransaction(regularTransaction)
        } catch {
          case t: Throwable =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
        }
      }
    }
  }

  private def validateAndSendTransaction(transaction: SidechainTypes#SCBT) = {
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

  /**
    * Create and sign a ForgingStakeTransaction, specifying inputs and outputs.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def spendForgingStake: Route = (post & path("spendForgingStake")) {
    entity(as[ReqSpendForgingStake]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val inputBoxes = wallet.allBoxes().asScala
          .filter(box => body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

        if (inputBoxes.length < body.transactionInputs.size) {
          ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput(s"Unable to find input(s)", None))
        } else {
          var inSum: Long = 0
          var outSum: Long = 0

          val inputs: JList[Pair[ForgerBox, PrivateKey25519]] = new JArrayList()
          inputBoxes.foreach(box => {
            var secret = wallet.secretByPublicKey(box.proposition())
            var privateKey = secret.get().asInstanceOf[PrivateKey25519]
            inputs.add(new Pair(box.asInstanceOf[ForgerBox], privateKey))
          })

          val outputs: JList[BoxData[_ <: Proposition]] = new JArrayList()
          body.regularOutputs.foreach(element =>
            outputs.add(new RegularBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value))
            )
          )
          body.forgerOutputs.foreach(element =>
            outputs.add(new ForgerBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value),
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.rewardKey.getOrElse(element.publicKey))),
              new VRFPublicKey(BytesUtils.fromHexString(element.vrfPubKey)))  // TODO: replace with VRFPublicKeySerializer later
            )
          )

          inputs.forEach(pair => inSum += pair.getKey.value())
          outputs.forEach(boxData => outSum += boxData.value())
          val fee: Long = inSum - outSum

          try {
            val transaction = ForgingStakeTransaction.create(
              inputs,
              outputs,
              fee, System.currentTimeMillis())

            if (body.format.getOrElse(false))
              ApiResponseUtil.toResponse(TransactionDTO(transaction))
            else
              ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(transaction))))
          } catch {
            case t: Throwable =>
              ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
          }
        }
      }
    }
  }

}


object SidechainTransactionRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllTransactions(format: Option[Boolean]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactions(transactions: List[SidechainTypes#SCBT]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllTransactionIds(transactionIds: List[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqFindById(transactionId: String, blockHash: Option[String], transactionIndex: Option[Boolean], format: Option[Boolean])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionDTO(transaction: SidechainTypes#SCBT) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionBytesDTO(transactionBytes: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqDecodeTransactionBytes(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespDecodeTransactionBytes(transaction: SidechainTypes#SCBT) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionInput(boxId: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionOutput(publicKey: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionForgerOutput(publicKey: String, rewardKey: Option[String], vrfPubKey: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateRegularTransaction(transactionInputs: List[TransactionInput],
                                                      regularOutputs: List[TransactionOutput],
                                                      withdrawalRequests: List[TransactionOutput],
                                                      forgerOutputs: List[TransactionForgerOutput],
                                                      format: Option[Boolean]) {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(regularOutputs.nonEmpty || withdrawalRequests.nonEmpty || forgerOutputs.nonEmpty, "Empty outputs")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateRegularTransactionSimplified(regularOutputs: List[TransactionOutput],
                                                                withdrawalRequests: List[TransactionOutput],
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
  private[api] case class ReqWithdrawCoins(outputs: List[TransactionOutput],
                                           @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateForgerCoins(outputs: List[TransactionForgerOutput],
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