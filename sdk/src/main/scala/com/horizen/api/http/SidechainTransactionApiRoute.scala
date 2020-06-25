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
import com.horizen.box.data.{ForgerBoxData, NoncedBoxData, RegularBoxData, WithdrawalRequestBoxData}
import com.horizen.box.{Box, NoncedBox, RegularBox}
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

case class SidechainTransactionApiRoute(override val settings: RESTApiSettings,
                                        sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef,
                                        companion: SidechainTransactionsCompanion,
                                        sidechainCoreTransactionFactory: SidechainCoreTransactionFactory,
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
    * Create and sign a core transaction, specifying inputs and outputs.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createCoreTransaction: Route = (post & path("createCoreTransaction")) {
    entity(as[ReqCreateCoreTransaction]) { body =>
      withNodeView { sidechainNodeView =>
        val wallet = sidechainNodeView.getNodeWallet
        val inputBoxes = wallet.allBoxes().asScala
          .filter(box => body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

        if (inputBoxes.length < body.transactionInputs.size) {
          ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput(s"Unable to find input(s)", None))
        } else {
          val outputs: JList[NoncedBoxData[Proposition, NoncedBox[Proposition]]] = new JArrayList()
          body.regularOutputs.foreach(element =>
            outputs.add(new RegularBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value)).asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])
          )

          body.withdrawalRequests.foreach(element =>
            outputs.add(new WithdrawalRequestBoxData(
              MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value)).asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])
          )

          body.forgerOutputs.foreach{element =>
            val forgerBoxToAdd = new ForgerBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value),
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
              VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
            )

            outputs.add(forgerBoxToAdd.asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])
          }

          val inputsTotalAmount: Long = inputBoxes.map(_.value()).sum
          val outputsTotalAmount: Long = outputs.asScala.map(_.value()).sum
          val fee: Long = inputsTotalAmount - outputsTotalAmount
          if(fee < 0) {
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError",
              Some(new IllegalArgumentException("Total inputs amount is less then total outputs amount."))))
          }
          else try {
            // Create unsigned tx
            val boxIds = inputBoxes.map(_.id()).asJava
            val timestamp = System.currentTimeMillis
            // Create a list of fake proofs for further messageToSign calculation
            val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)

            val unsignedTransaction = sidechainCoreTransactionFactory.create(boxIds, outputs, fakeProofs, fee, timestamp)

            // Create signed tx. Note: we suppose that box use proposition that require general secret.sign(...) usage only.
            val messageToSign = unsignedTransaction.messageToSign()
            val proofs = inputBoxes.map(box => {
              wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
            })

            val transaction = sidechainCoreTransactionFactory.create(boxIds, outputs, proofs.asJava, fee, timestamp)
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

  /**
    * Create and sign a core transaction, specifying core outputs and fee. Search for and spend proper amount of regular coins.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createCoreTransactionSimplified: Route = (post & path("createCoreTransactionSimplified")) {
    entity(as[ReqCreateCoreTransactionSimplified]) { body =>
      withNodeView { sidechainNodeView =>
        var outputList = body.regularOutputs
        var withdrawalRequestList = body.withdrawalRequests
        var forgerOutputList = body.forgerOutputs
        var fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[RegularBox], outputList, withdrawalRequestList, forgerOutputList, fee, changeAddress, wallet, sidechainNodeView) match {
              case Success(transaction) =>
                if (body.format.getOrElse(false))
                  ApiResponseUtil.toResponse(TransactionDTO(transaction))
                else
                  ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(transaction))))
              case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(e)))
            }
          case None =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError",
              Some(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))))
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
      withNodeView { sidechainNodeView =>
        val outputList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[RegularBox], outputList, List(), List(), fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView) match {
              case Success(transaction) => validateAndSendTransaction(transaction)
              case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(e)))
            }
          case None =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError",
              Some(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))))
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

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[RegularBox], List(), withdrawalOutputsList, List(), fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView) match {
              case Success(transaction) => validateAndSendTransaction(transaction)
              case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(e)))
            }
          case None =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError",
              Some(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))))
        }
      }
    }
  }

  def makeForgerStake: Route = (post & path("makeForgerStake")) {
    entity(as[ReqCreateForgerStake]) { body =>
      withNodeView { sidechainNodeView =>
        val forgerOutputsList = body.outputs
        val fee = body.fee
        val wallet = sidechainNodeView.getNodeWallet

        getChangeAddress(wallet) match {
          case Some(changeAddress) =>
            createCoreTransaction(classOf[RegularBox], List(), List(), forgerOutputsList, fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView) match {
              case Success(transaction) => validateAndSendTransaction(transaction)
              case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(e)))
            }
          case None =>
            ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError",
              Some(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))))
        }
      }
    }
  }

  /**
    * Create and sign a CoreTransaction, specifying inputs and outputs, add that transaction to the memory pool
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
          val outputs: JList[NoncedBoxData[Proposition, NoncedBox[Proposition]]] = new JArrayList()
          body.regularOutputs.foreach(element =>
            outputs.add(new RegularBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value)).asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]]
            )
          )
          body.forgerOutputs.foreach{element =>
            val forgerBoxToAdd = new ForgerBoxData(
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
              new lang.Long(element.value),
              PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
              VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
            )

            outputs.add(forgerBoxToAdd.asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])
          }

          val inputsTotalAmount: Long = inputBoxes.map(_.value()).sum
          val outputsTotalAmount: Long = outputs.asScala.map(_.value()).sum
          val fee: Long = inputsTotalAmount - outputsTotalAmount

          try {
            // Create unsigned tx
            val boxIds = inputBoxes.map(_.id()).asJava
            val timestamp = System.currentTimeMillis
            // Create a list of fake proofs for further messageToSign calculation
            val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)
            val unsignedTransaction = sidechainCoreTransactionFactory.create(boxIds, outputs, fakeProofs, fee, timestamp)

            // Create signed tx. Note: we suppose that box use proposition that require general secret.sign(...) usage only.
            val messageToSign = unsignedTransaction.messageToSign()
            val proofs = inputBoxes.map(box => {
              wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
            })

            val transaction: SidechainCoreTransaction = sidechainCoreTransactionFactory.create(boxIds, outputs, proofs.asJava, fee, timestamp)
            val txRepresentation: (SidechainTypes#SCBT => SuccessResponse) =
              if (body.format.getOrElse(false)) {
                tx => TransactionDTO(tx)
              } else {
                tx => TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(tx)))
              }
            validateAndSendTransaction(transaction, txRepresentation)
          } catch {
            case t: Throwable =>
              ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", Some(t)))
          }
        }
      }
    }
  }

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: (SidechainTypes#SCBT => SuccessResponse) = {
    transaction => TransactionIdDTO(transaction.id)
  }

  private def validateAndSendTransaction(transaction: SidechainTypes#SCBT,
                                         transactionResponseRepresentation: (SidechainTypes#SCBT => SuccessResponse) = defaultTransactionResponseRepresentation) = {
    withNodeView {
      sidechainNodeView =>
        val barrier = Await.result(
          sidechainTransactionActorRef ? BroadcastTransaction(transaction),
          settings.timeout).asInstanceOf[Future[Unit]]
        onComplete(barrier) {
          case Success(_) =>
            ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
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

  // try to get the first PublicKey25519Proposition in the wallet
  // None - if not present.
  private def getChangeAddress(wallet: NodeWallet): Option[PublicKey25519Proposition] = {
    wallet.allSecrets().asScala
      .find(s => s.publicImage().isInstanceOf[PublicKey25519Proposition])
      .map(_.publicImage().asInstanceOf[PublicKey25519Proposition])
  }

  private def createCoreTransaction(inputBoxesType: Class[_<: Box[_ <: Proposition]],
                                    regularBoxDataList: List[TransactionOutput],
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

    val outputs: JList[NoncedBoxData[Proposition, NoncedBox[Proposition]]] = new JArrayList()
    regularBoxDataList.foreach(element =>
      outputs.add(new RegularBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
        new lang.Long(element.value)).asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])
    )

    withdrawalRequestBoxDataList.foreach(element =>
      outputs.add(new WithdrawalRequestBoxData(
        // Keep in mind that check MC rpc `getnewaddress` returns standard address with hash inside in LE
        // different to `getnewaddress "" true` hash that is in BE endianness.
        MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHorizenPublicKeyAddress(element.publicKey, params)),
        new lang.Long(element.value)).asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])
    )

    forgerBoxDataList.foreach{element =>
      val forgingBoxToAdd = new ForgerBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.publicKey)),
        new lang.Long(element.value),
        PublicKey25519PropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
        VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
      )

      outputs.add(forgingBoxToAdd.asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])
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
      outputs.add(new RegularBoxData(changeAddress, inputsTotalAmount - inputsMinimumExpectedAmount).asInstanceOf[NoncedBoxData[Proposition, NoncedBox[Proposition]]])

    // Create unsigned tx
    val boxIds = boxes.map(_.id()).asJava
    val timestamp = System.currentTimeMillis
    // Create a list of fake proofs for further messageToSign calculation
    val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)
    val unsignedTransaction = sidechainCoreTransactionFactory.create(boxIds, outputs, fakeProofs, fee, timestamp)

    // Create signed tx. Note: we suppose that box use proposition that require general secret.sign(...) usage only.
    val messageToSign = unsignedTransaction.messageToSign()
    val proofs = boxes.map(box => {
      wallet.secretByPublicKey(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
    })

    sidechainCoreTransactionFactory.create(boxIds, outputs, proofs.asJava, fee, timestamp)
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
  private[api] case class TransactionWithdrawalRequestOutput(publicKey: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

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