package com.horizen.api.http

import java.lang
import java.util.{Collections, ArrayList => JArrayList, List => JList}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.SidechainTransactionErrorResponse._
import com.horizen.api.http.SidechainTransactionRestScheme._
import com.horizen.box.data.{ForgerBoxData, NoncedBoxData, WithdrawalRequestBoxData, ZenBoxData}
import com.horizen.box.{Box, ZenBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.{NodeWallet, SidechainNodeView}
import com.horizen.params.NetworkParams
import com.horizen.proof.Proof
import com.horizen.proposition._
import com.horizen.serialization.Views
import com.horizen.transaction._
import com.horizen.utils.BytesUtils
import scorex.core.settings.RESTApiSettings

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.Breaks._
import scala.util.{Failure, Success, Try}
import java.util.{Optional => JOptional}

case class SidechainTransactionApiRoute(override val settings: RESTApiSettings,
                                        sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef,
                                        companion: SidechainTransactionsCompanion,
                                        params: NetworkParams)
                                       (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute with SidechainTypes {

  override val route: Route = (pathPrefix("transaction")) {
    allTransactions ~ findById ~ decodeTransactionBytes ~ createCoreTransaction ~ createCoreTransactionSimplified ~
    sendCoinsToAddress ~ sendTransaction ~ withdrawCoins ~ makeForgerStake ~ spendForgingStake
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
      applyOnNodeView { sidechainNodeView =>
        val memoryPool = sidechainNodeView.getNodeMemoryPool
        val history = sidechainNodeView.getNodeHistory

        def searchTransactionInMemoryPool(id: String): Option[SidechainTypes#SCBT] = {
          val opt = memoryPool.getTransactionById(id)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        def searchTransactionInBlock(id: String, blockHash: String): Option[SidechainTypes#SCBT] = {
          val opt = history.searchTransactionInsideSidechainBlock(id, blockHash)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        def searchTransactionInBlockchain(id: String): Option[SidechainTypes#SCBT] = {
          val opt = history.searchTransactionInsideBlockchain(id)
          if (opt.isPresent)
            Option(opt.get())
          else None
        }

        val txId = body.transactionId
        val format = body.format.getOrElse(false)
        val blockHash = body.blockHash.getOrElse("")
        val txIndex = body.transactionIndex.getOrElse(false)
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
            ApiResponseUtil.toResponse(ErrorNotFoundTransactionId(error, JOptional.empty()))
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

  /**
    * Create and sign a core transaction, specifying inputs and outputs.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createCoreTransaction: Route = (post & path("createCoreTransaction")) {
    entity(as[ReqCreateCoreTransaction]) { body =>
      applyOnNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val inputBoxes = wallet.allBoxes().asScala
          .filter(box => body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

        if (inputBoxes.length < body.transactionInputs.size) {
          ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput(s"Unable to find input(s)", JOptional.empty()))
        } else {
          val outputs: JList[NoncedBoxData[Proposition, Box[Proposition]]] = new JArrayList()
          body.regularOutputs.foreach(element =>
            outputs.add(new ZenBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              element.value).asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])
          )

          body.withdrawalRequests.foreach(element =>
            outputs.add(new WithdrawalRequestBoxData(
              MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.mainchainAddress)),
              element.value).asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])
          )

          body.forgerOutputs.foreach{element =>
            val forgerBoxToAdd = new ForgerBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value),
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
              VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
            )

            outputs.add(forgerBoxToAdd.asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])
          }

          val inputsTotalAmount: Long = inputBoxes.map(_.value()).sum
          val outputsTotalAmount: Long = outputs.asScala.map(_.value()).sum
          val fee: Long = inputsTotalAmount - outputsTotalAmount
          if(fee < 0) {
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError",
              JOptional.of(new IllegalArgumentException("Total inputs amount is less then total outputs amount."))))
          }
          else try {
            // Create unsigned tx
            val boxIds = inputBoxes.map(_.id()).asJava
            // Create a list of fake proofs for further messageToSign calculation
            val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)

            val unsignedTransaction = new SidechainCoreTransaction(boxIds, outputs, fakeProofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)

            // Create signed tx. Note: we suppose that box use proposition that require general secret.sign(...) usage only.
            val messageToSign = unsignedTransaction.messageToSign()
            val proofs = inputBoxes.map(box => {
              wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
            })

            val transaction = new SidechainCoreTransaction(boxIds, outputs, proofs.asJava, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)
            if (body.format.getOrElse(false))
              ApiResponseUtil.toResponse(TransactionDTO(transaction))
            else
              ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(transaction))))
          } catch {
            case t: Throwable =>
              ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(t)))
          }
        }
      }
    }
  }

  /**
    * Create and sign a core transaction, specifying core outputs and fee. Search for and spend proper amount of regular coins.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createCoreTransactionSimplified: Route = (post & path("createCoreTransactionSimplified")) {
    entity(as[ReqCreateCoreTransactionSimplified]) { body =>
      applyOnNodeView { sidechainNodeView =>
        val outputList = body.regularOutputs
        val withdrawalRequestList = body.withdrawalRequests
        val forgerOutputList = body.forgerOutputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[ZenBox], outputList, withdrawalRequestList, forgerOutputList, fee, changeAddress, wallet, sidechainNodeView) match {
              case Success(transaction) =>
                if (body.format.getOrElse(false))
                  ApiResponseUtil.toResponse(TransactionDTO(transaction))
                else
                  ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(transaction))))
              case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
            }
          case None =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError",
              JOptional.of(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))))
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
        val outputList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[ZenBox], outputList, List(), List(), fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView)
          case None =>
            Failure(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))
        }
      } match {
        case Success(transaction) => validateAndSendTransaction(transaction)
        case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
      }
    }
  }

  def withdrawCoins: Route = (post & path("withdrawCoins")) {
    entity(as[ReqWithdrawCoins]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        val withdrawalOutputsList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[ZenBox], List(), withdrawalOutputsList, List(), fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView)
          case None =>
            Failure(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))
        }
      } match {
        case Success(transaction) => validateAndSendTransaction(transaction)
        case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
      }
    }
  }

  def makeForgerStake: Route = (post & path("makeForgerStake")) {
    entity(as[ReqCreateForgerStake]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        val forgerOutputsList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[ZenBox], List(), List(), forgerOutputsList, fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView)
          case None =>
            Failure(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))
        }
      } match {
        case Success(transaction) => validateAndSendTransaction(transaction)
        case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
      }
    }
  }

  /**
    * Create and sign a CoreTransaction, specifying inputs and outputs, add that transaction to the memory pool
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def spendForgingStake: Route = (post & path("spendForgingStake")) {
    entity(as[ReqSpendForgingStake]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val inputBoxes = wallet.allBoxes().asScala
          .filter(box => body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

        if (inputBoxes.length < body.transactionInputs.size) {
          Left(ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput(s"Unable to find input(s)", JOptional.empty())))
        } else {
          val outputs: JList[NoncedBoxData[Proposition, Box[Proposition]]] = new JArrayList()
          body.regularOutputs.foreach(element =>
            outputs.add(new ZenBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              element.value).asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]]
            )
          )
          body.forgerOutputs.foreach{element =>
            val forgerBoxToAdd = new ForgerBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              element.value,
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
              VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
            )

            outputs.add(forgerBoxToAdd.asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])
          }

          val inputsTotalAmount: Long = inputBoxes.map(_.value()).sum
          val outputsTotalAmount: Long = outputs.asScala.map(_.value()).sum
          val fee: Long = inputsTotalAmount - outputsTotalAmount

          try {
            // Create unsigned tx
            val boxIds = inputBoxes.map(_.id()).asJava
            // Create a list of fake proofs for further messageToSign calculation
            val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)
            val unsignedTransaction = new SidechainCoreTransaction(boxIds, outputs, fakeProofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)

            // Create signed tx. Note: we suppose that box use proposition that require general secret.sign(...) usage only.
            val messageToSign = unsignedTransaction.messageToSign()
            val proofs = inputBoxes.map(box => {
              wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
            })

            val transaction: SidechainCoreTransaction = new SidechainCoreTransaction(boxIds, outputs, proofs.asJava, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)
            val txRepresentation: (SidechainTypes#SCBT => SuccessResponse) =
              if (body.format.getOrElse(false)) {
                tx => TransactionDTO(tx)
              } else {
                tx => TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(tx)))
              }
            Right((transaction, txRepresentation))
          } catch {
            case t: Throwable =>
              Left(ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(t))))
          }
        }
      } match {
        case Left(errorResponse) => errorResponse
        case Right((transaction, txRepresentation)) => validateAndSendTransaction(transaction, txRepresentation)
      }
    }
  }

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: (SidechainTypes#SCBT => SuccessResponse) = {
    transaction => TransactionIdDTO(transaction.id)
  }

  private def validateAndSendTransaction(transaction: SidechainTypes#SCBT,
                                         transactionResponseRepresentation: (SidechainTypes#SCBT => SuccessResponse) = defaultTransactionResponseRepresentation) = {
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

  /**
    * Validate and send a transaction, given its serialization as input.
    * Return error in case of invalid transaction or parsing error, otherwise return the id of the transaction.
    */
  def sendTransaction: Route = (post & path("sendTransaction")) {
    entity(as[ReqSendTransactionPost]) { body =>
      val transactionBytes = BytesUtils.fromHexString(body.transactionBytes)
      companion.parseBytesTry(transactionBytes) match {
        case Success(transaction) =>
          validateAndSendTransaction(transaction)
        case Failure(exception) =>
          ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exception)))
      }
    }
  }

  // try to get the first PublicKey25519Proposition in the wallet
  // None - if not present.
  private def getChangeAddress(wallet: NodeWallet): Option[PublicKey25519Proposition] = {
    wallet.allSecrets().asScala
      .find(s => s.publicImage().isInstanceOf[PublicKey25519Proposition])
      .map(_.publicImage().asInstanceOf[PublicKey25519Proposition])
  }

  private def createCoreTransaction(inputBoxesType: Class[_<: Box[_ <: Proposition]],
                                    zenBoxDataList: List[TransactionOutput],
                                    withdrawalRequestBoxDataList: List[TransactionWithdrawalRequestOutput],
                                    forgerBoxDataList: List[TransactionForgerOutput],
                                    fee: Long,
                                    changeAddress: PublicKey25519Proposition,
                                    wallet: NodeWallet,
                                    sidechainNodeView: SidechainNodeView): Try[SidechainCoreTransaction] = Try {

    val memoryPool = sidechainNodeView.getNodeMemoryPool
    val boxIdsToExclude: JArrayList[Array[scala.Byte]] = new JArrayList()

    for(transaction <- memoryPool.getTransactionsSortedByFee(memoryPool.getSize).asScala)
      for(id <- transaction.boxIdsToOpen().asScala)
        boxIdsToExclude.add(id.data)

    val outputs: JList[NoncedBoxData[Proposition, Box[Proposition]]] = new JArrayList()
    zenBoxDataList.foreach(element =>
      outputs.add(new ZenBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
        element.value).asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])
    )

    withdrawalRequestBoxDataList.foreach(element =>
      outputs.add(new WithdrawalRequestBoxData(
        // Keep in mind that check MC rpc `getnewaddress` returns standard address with hash inside in LE
        // different to `getnewaddress "" true` hash that is in BE endianness.
        MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHorizenPublicKeyAddress(element.mainchainAddress, params)),
        element.value).asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])
    )

    forgerBoxDataList.foreach{element =>
      val forgingBoxToAdd = new ForgerBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
        element.value,
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
        VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
      )

      outputs.add(forgingBoxToAdd.asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])
    }


    val outputsTotalAmount: Long = outputs.asScala.map(boxData => boxData.value()).sum
    val inputsMinimumExpectedAmount: Long = outputsTotalAmount + fee
    var inputsTotalAmount: Long = 0L

    val boxes = ArrayBuffer[Box[Proposition]]()
    breakable {
      for (box: Box[Proposition] <- wallet.boxesOfType(inputBoxesType, boxIdsToExclude).asScala) {
        boxes.append(box)
        inputsTotalAmount += box.value()
        if (inputsTotalAmount >= inputsMinimumExpectedAmount)
          break
      }
    }


    if(inputsTotalAmount < inputsMinimumExpectedAmount)
      throw new IllegalArgumentException("Not enough balances in the wallet to create transaction.")

    // Add change if need.
    if(inputsTotalAmount > inputsMinimumExpectedAmount)
      outputs.add(new ZenBoxData(changeAddress, inputsTotalAmount - inputsMinimumExpectedAmount).asInstanceOf[NoncedBoxData[Proposition, Box[Proposition]]])

    // Create unsigned tx
    val boxIds = boxes.map(_.id()).asJava
    // Create a list of fake proofs for further messageToSign calculation
    val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)
    val unsignedTransaction = new SidechainCoreTransaction(boxIds, outputs, fakeProofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)

    // Create signed tx.
    val messageToSign = unsignedTransaction.messageToSign()
    val proofs = boxes.map(box => {
      wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
    })

    new SidechainCoreTransaction(boxIds, outputs, proofs.asJava, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)
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

object SidechainTransactionErrorResponse {

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