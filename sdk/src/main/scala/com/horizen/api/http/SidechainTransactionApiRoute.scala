package com.horizen.api.http

import java.lang
import java.util.{Collections, ArrayList => JArrayList, List => JList}
import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainTransactionErrorResponse._
import com.horizen.api.http.SidechainTransactionRestScheme._
import com.horizen.api.http.TransactionBaseErrorResponse.ErrorBadCircuit
import com.horizen.api.http.TransactionBaseRestScheme.{TransactionBytesDTO, TransactionDTO}
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.box.data.{BoxData, ForgerBoxData, WithdrawalRequestBoxData, ZenBoxData}
import com.horizen.box.{Box, ZenBox}
import com.horizen.box.ForgerBox
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node._
import com.horizen.params.NetworkParams
import com.horizen.proof.{Proof, SchnorrSignatureSerializer}
import com.horizen.proposition._
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.Views
import com.horizen.transaction._
import com.horizen.utils.{BytesUtils, ZenCoinsUtils}
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import com.horizen.utils.{Pair => JPair}
import com.horizen.cryptolibprovider.utils.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.control.Breaks._
import scala.util.{Failure, Success, Try}

case class SidechainTransactionApiRoute(override val settings: RESTApiSettings,
                                        sidechainNodeViewHolderRef: ActorRef,
                                        sidechainTransactionActorRef: ActorRef,
                                        companion: SidechainTransactionsCompanion,
                                        params: NetworkParams,
                                        circuitType: CircuitTypes)
                                       (implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends TransactionBaseApiRoute[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView](sidechainTransactionActorRef, companion)  with SidechainTypes {

  override implicit val tag: ClassTag[SidechainNodeView] = ClassTag[SidechainNodeView](classOf[SidechainNodeView])

  override val route: Route = pathPrefix("transaction") {
    allTransactions ~ findById ~ decodeTransactionBytes ~ createCoreTransaction ~ createCoreTransactionSimplified ~
    sendCoinsToAddress ~ sendTransaction ~ withdrawCoins ~ makeForgerStake ~ spendForgingStake ~
      createOpenStakeTransaction ~ createOpenStakeTransactionSimplified ~ createKeyRotationTransaction
  }


  /**
    * Follows the same behaviour as the corresponding RPC call in zend: by default it will look for
    * transaction in memory pool. Additional parameters are:
    * -format: if true a JSON representation of transaction is returned, otherwise return the transaction serialized as
    * a hex string. If format is not specified, false behaviour is assumed as default;
    * -blockHash: If specified, will look for tx in the corresponding block
    *
    * All the possible behaviours are be:
    * 1) blockHash set -> Search in block referenced by blockHash
    * 2) blockHash not set -> Search in memory pool
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

        val txId = body.transactionId
        val format = body.format.getOrElse(false)
        var transaction: Option[SidechainTypes#SCBT] = None
        var error: String = ""

        body.blockHash match {
          case Some(hash) =>
            // Search in block referenced by blockHash
            transaction = searchTransactionInBlock(txId, hash)
            if (transaction.isEmpty)
              error = s"Transaction $txId not found in specified block"

          case None =>
            // Search in memory pool
            transaction = searchTransactionInMemoryPool(txId)
            if (transaction.isEmpty)
              error = s"Transaction $txId not found in memory pool"
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
    * Create and sign a core transaction, specifying inputs and outputs.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createCoreTransaction: Route = (post & path("createCoreTransaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateCoreTransaction]) { body =>
          applyOnNodeView { sidechainNodeView =>
            val wallet = sidechainNodeView.getNodeWallet
            val inputBoxes = wallet.allBoxes().asScala
              .filter(box =>
                (box.isInstanceOf[ZenBox] || box.isInstanceOf[ForgerBox]) &&
                  body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

            if (inputBoxes.length < body.transactionInputs.size) {
              ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput(s"Unable to find input(s)", JOptional.empty()))
            } else {
              val outputs: JList[BoxData[Proposition, Box[Proposition]]] = new JArrayList()
              body.regularOutputs.foreach(element =>
                outputs.add(new ZenBoxData(
                  PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.publicKey)),
                  element.value).asInstanceOf[BoxData[Proposition, Box[Proposition]]])
              )

              body.withdrawalRequests.foreach(element =>
                outputs.add(new WithdrawalRequestBoxData(
                  MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHorizenPublicKeyAddress(element.mainchainAddress, params)),
                  element.value).asInstanceOf[BoxData[Proposition, Box[Proposition]]])
              )

              body.forgerOutputs.foreach{element =>
                val forgerBoxToAdd = new ForgerBoxData(
                  PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.publicKey)),
                  new lang.Long(element.value),
                  PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
                  VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
                )

                outputs.add(forgerBoxToAdd.asInstanceOf[BoxData[Proposition, Box[Proposition]]])
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

                body.withdrawalRequests.foreach(element =>
                  if (element.value < ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)) {
                    throw new IllegalArgumentException("Withdrawal transaction amount is below the MC dust threshold value.")
                  })

                val unsignedTransaction = new SidechainCoreTransaction(boxIds, outputs, fakeProofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)

                // Create signed tx. Note: we suppose that box use proposition that require general secret.sign(...) usage only.
                val messageToSign = unsignedTransaction.messageToSign()
                val proofs = inputBoxes.map(box => {
                  if (box.proposition().isInstanceOf[PublicKey25519Proposition]) {
                    wallet.secretByPublicKey25519Proposition(box.proposition().asInstanceOf[PublicKey25519Proposition]).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
                  }else{
                    throw new IllegalArgumentException(s"Unexpected box locking proposition for box id [${BytesUtils.toHexString(box.id())}]. Expected: PublicKey25519Proposition, got ${box.proposition().getClass}")
                  }
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
    }
  }

  /**
    * Create and sign a core transaction, specifying core outputs and fee. Search for and spend proper amount of regular coins.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def createCoreTransactionSimplified: Route = (post & path("createCoreTransactionSimplified")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateCoreTransactionSimplified]) { body =>
          applyOnNodeView { sidechainNodeView =>
            val outputList = body.regularOutputs
            val withdrawalRequestList = body.withdrawalRequests
            val forgerOutputList = body.forgerOutputs
            val fee = body.fee
            val wallet = sidechainNodeView.getNodeWallet

            getChangeAddress(wallet) match {
              case Some(changeAddress) =>
                createCoreTransaction(outputList, withdrawalRequestList, forgerOutputList, fee, changeAddress, wallet, sidechainNodeView) match {
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
    }
  }

  /**
    * Create and sign a core transaction, specifying regular outputs and fee. Search for and spend proper amount of regular coins. Then validate and send the transaction.
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def sendCoinsToAddress: Route = (post & path("sendCoinsToAddress")) {
    withBasicAuth {
      _ => {
        entity(as[ReqSendCoinsToAddress]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val outputList = body.outputs
            val fee = body.fee
            val wallet = sidechainNodeView.getNodeWallet

            getChangeAddress(wallet) match {
              case Some(changeAddress) =>
                createCoreTransaction(outputList, List(), List(), fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView)
              case None =>
                Failure(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))
            }
          } match {
            case Success(transaction) =>
              if (body.automaticSend.getOrElse(true)) {
                validateAndSendTransaction(transaction)
              } else {
                if (body.format.getOrElse(false))
                  ApiResponseUtil.toResponse(TransactionDTO(transaction))
                else
                  ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(transaction))))
              }
            case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
          }
        }
      }
    }
  }

  def withdrawCoins: Route = (post & path("withdrawCoins")) {
    withBasicAuth {
      _ => {
        entity(as[ReqWithdrawCoins]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val withdrawalOutputsList = body.outputs
            val fee = body.fee
            val wallet = sidechainNodeView.getNodeWallet

            getChangeAddress(wallet) match {
              case Some(changeAddress) =>
                createCoreTransaction(List(), withdrawalOutputsList, List(), fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView)
              case None =>
                Failure(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))
            }
          } match {
            case Success(transaction) => validateAndSendTransaction(transaction)
            case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
          }
        }
      }
    }
  }

  def makeForgerStake: Route = (post & path("makeForgerStake")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateForgerStake]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val forgerOutputsList = body.outputs
            val fee = body.fee
            val wallet = sidechainNodeView.getNodeWallet

            getChangeAddress(wallet) match {
              case Some(changeAddress) =>
                createCoreTransaction(List(), List(), forgerOutputsList, fee.getOrElse(0L), changeAddress, wallet, sidechainNodeView)
              case None =>
                Failure(new IllegalStateException("Can't find change address in wallet. Please, create a PrivateKey secret first."))
            }
          } match {
            case Success(transaction) => validateAndSendTransaction(transaction)
            case Failure(e) => ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(e)))
          }
        }
      }
    }
  }

  /**
    * Create and sign a CoreTransaction, specifying inputs and outputs, add that transaction to the memory pool
    * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
    */
  def spendForgingStake: Route = (post & path("spendForgingStake")) {
    withBasicAuth {
      _ => {
        entity(as[ReqSpendForgingStake]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val wallet = sidechainNodeView.getNodeWallet
            val inputBoxes = wallet.allBoxes().asScala
              .filter(box =>
                (box.isInstanceOf[ZenBox] || box.isInstanceOf[ForgerBox]) &&
                  body.transactionInputs.exists(p => p.boxId.contentEquals(BytesUtils.toHexString(box.id()))))

            if (inputBoxes.length < body.transactionInputs.size) {
              Left(ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput(s"Unable to find input(s)", JOptional.empty())))
            } else {
              val outputs: JList[BoxData[Proposition, Box[Proposition]]] = new JArrayList()
              body.regularOutputs.foreach(element =>
                outputs.add(new ZenBoxData(
                  PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.publicKey)),
                  element.value).asInstanceOf[BoxData[Proposition, Box[Proposition]]]
                )
              )
              body.forgerOutputs.foreach{element =>
                val forgerBoxToAdd = new ForgerBoxData(
                  PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.publicKey)),
                  element.value,
                  PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
                  VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
                )

                outputs.add(forgerBoxToAdd.asInstanceOf[BoxData[Proposition, Box[Proposition]]])
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
                  if (box.proposition().isInstanceOf[PublicKey25519Proposition]) {
                    wallet.secretByPublicKey25519Proposition(box.proposition().asInstanceOf[PublicKey25519Proposition]).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
                  }else{
                    throw new IllegalArgumentException(s"Unexpected box locking proposition for box id [${BytesUtils.toHexString(box.id())}]. Expected: PublicKey25519Proposition, got ${box.proposition().getClass}")
                  }
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
    }
  }

  def createOpenStakeTransaction: Route = (post & path("createOpenStakeTransaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqOpenStake]) { body =>
          buildOpenStakeTransaction(body) match {
            case Success(tx) =>
              if (body.automaticSend.getOrElse(true)) {
                validateAndSendTransaction(tx.asInstanceOf[SidechainTypes#SCBT])
              } else {
                if (body.format.getOrElse(false))
                  ApiResponseUtil.toResponse(TransactionDTO(tx.asInstanceOf[SCBT]))
                else
                  ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(tx.asInstanceOf[SCBT]))))
              }
            case Failure(exception) =>
              ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exception)))
          }
        }
      }
    }
  }

  def createOpenStakeTransactionSimplified: Route = (post & path("createOpenStakeTransactionSimplified")) {
    withBasicAuth {
      _ => {
        entity(as[ReqOpenStakeSimplified]) { body =>
          buildOpenStakeTransactionSimplified(body) match {
            case Success(tx) =>
              if (body.automaticSend.getOrElse(true)) {
                validateAndSendTransaction(tx.asInstanceOf[SidechainTypes#SCBT])
              } else {
                if (body.format.getOrElse(false))
                  ApiResponseUtil.toResponse(TransactionDTO(tx.asInstanceOf[SCBT]))
                else
                  ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(tx.asInstanceOf[SCBT]))))
              }
            case Failure(exception) =>
              ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exception)))     }
        }
      }
    }
  }

  def createKeyRotationTransaction: Route = (post & path("createKeyRotationTransaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateKeyRotationTransaction]) { body =>
          circuitType match {
            case NaiveThresholdSignatureCircuit =>
              ApiResponseUtil.toResponse(ErrorBadCircuit("The current circuit doesn't support key rotation transaction!", JOptional.empty()))
            case NaiveThresholdSignatureCircuitWithKeyRotation =>
              applyOnNodeView { sidechainNodeView =>
                val wallet = sidechainNodeView.getNodeWallet
                val fee = body.fee.getOrElse(0L)

                val memoryPool = sidechainNodeView.getNodeMemoryPool
                val boxIdsToExclude: JArrayList[Array[scala.Byte]] = new JArrayList()
                for(transaction <- memoryPool.getTransactions().asScala)
                  for(id <- transaction.boxIdsToOpen().asScala) {
                    boxIdsToExclude.add(id.data)
                  }

                //Collect input box
                wallet.boxesOfType(classOf[ZenBox], boxIdsToExclude).asScala.find(box => box.value() >= fee) match {
                  case Some(inputBox) =>
                    Try { CertificateKeyRotationTransaction.create(
                      new JPair[ZenBox, PrivateKey25519](inputBox.asInstanceOf[ZenBox], wallet.secretByPublicKey25519Proposition(inputBox.proposition().asInstanceOf[PublicKey25519Proposition]).get()),
                      inputBox.proposition().asInstanceOf[PublicKey25519Proposition],
                      fee,
                      body.keyType,
                      body.keyIndex,
                      SchnorrPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.newKey)),
                      SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.signingKeySignature)),
                      SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.masterKeySignature)),
                      SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.newKeySignature)),
                    )
                    } match {
                      case Success(keyRotationTransaction) =>if (body.automaticSend.getOrElse(true)) {
                      validateAndSendTransaction(keyRotationTransaction.asInstanceOf[SidechainTypes#SCBT])
                    } else {
                      if (body.format.getOrElse(false)) {
                        ApiResponseUtil.toResponse(TransactionDTO(keyRotationTransaction.asInstanceOf[SCBT]))
                      } else {
                        ApiResponseUtil.toResponse(TransactionBytesDTO(BytesUtils.toHexString(companion.toBytes(keyRotationTransaction.asInstanceOf[SCBT]))))
                      }}
                      case Failure(ex) =>
                        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(ex)))
                    }
                  case None =>
                    ApiResponseUtil.toResponse(ErrorNotFoundTransactionInput("Not found input box to pay the fee", JOptional.empty()))
                }

              }
          }
        }
      }
    }
  }

  private def buildOpenStakeTransactionSimplified(body: ReqOpenStakeSimplified): Try[OpenStakeTransaction] = {
    applyOnNodeView { sidechainNodeView =>
      val wallet = sidechainNodeView.getNodeWallet

      //Collect input box
      wallet.allBoxes().asScala
        .find(box => box.proposition().equals(PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(body.forgerProposition)))
          && box.value() >= body.fee.getOrElse(0L)) match {
        case Some(inputBox) =>
          //Collect input private key
          val inputPrivateKey = wallet.secretByPublicKey25519Proposition(inputBox.proposition().asInstanceOf[PublicKey25519Proposition]).get().asInstanceOf[PrivateKey25519]
          //Create openStakeTransaction
          createAndSignOpenStakeTransaction(inputBox, inputPrivateKey, body.forgerProposition, body.forgerIndex, body.fee)
        case None =>
          throw new IllegalArgumentException("Unable to find input for Proposition "+body.forgerProposition)
      }
    }
  }

  /**
   * Create and sign a openStakeTransaction
   * @return a signed OpenStakeTransaction or a GenericTransactionError
   */
  private def buildOpenStakeTransaction(body: ReqOpenStake): Try[OpenStakeTransaction] = {
    applyOnNodeView { sidechainNodeView =>
      val wallet = sidechainNodeView.getNodeWallet

      //Collect input box
      wallet.allBoxes().asScala
        .find(box => BytesUtils.toHexString(box.id()).equals(body.transactionInput.boxId)) match {
        case Some(inputBox) =>
          //Collect input private key
          val inputPrivateKey = wallet.secretByPublicKey25519Proposition(inputBox.proposition().asInstanceOf[PublicKey25519Proposition]).get().asInstanceOf[PrivateKey25519]
          //Create openStakeTransaction
          createAndSignOpenStakeTransaction(inputBox, inputPrivateKey, body.regularOutputProposition, body.forgerIndex, body.fee)
        case None =>
          throw new IllegalArgumentException("Unable to find input!")
      }
    }
  }


  private def createAndSignOpenStakeTransaction(inputBox: Box[Proposition], inputPrivateKey: PrivateKey25519, outputProposition: String,
                                                forgerIndex: Int, inputFee: Option[Long]): Try[OpenStakeTransaction] = {
    //Collect fee
    val fee = inputFee.getOrElse(0L)
    if (fee < 0) {
      throw new IllegalArgumentException("Fee can't be negative!")
    }
    if (fee > inputBox.value) {
      throw new IllegalArgumentException("Fee can't be greater than the input!")
    }

    Try {
      //Create the openStakeTransaction
      OpenStakeTransaction.create(new JPair[ZenBox,PrivateKey25519](inputBox.asInstanceOf[ZenBox],inputPrivateKey),
        PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(outputProposition)),
        forgerIndex,
        fee
      )
    }
  }

  // try to get the first PublicKey25519Proposition in the wallet
  // None - if not present.
  private def getChangeAddress(wallet: NodeWallet): Option[PublicKey25519Proposition] = {
    wallet.allSecrets().asScala
      .find(s => s.publicImage().isInstanceOf[PublicKey25519Proposition])
      .map(_.publicImage().asInstanceOf[PublicKey25519Proposition])
  }

  private def createCoreTransaction(zenBoxDataList: List[TransactionOutput],
                                    withdrawalRequestBoxDataList: List[TransactionWithdrawalRequestOutput],
                                    forgerBoxDataList: List[TransactionForgerOutput],
                                    fee: Long,
                                    changeAddress: PublicKey25519Proposition,
                                    wallet: NodeWallet,
                                    sidechainNodeView: SidechainNodeView): Try[SidechainCoreTransaction] = Try {

    val memoryPool = sidechainNodeView.getNodeMemoryPool
    val boxIdsToExclude: JArrayList[Array[scala.Byte]] = new JArrayList()

    for(transaction <- memoryPool.getTransactions().asScala)
      for(id <- transaction.boxIdsToOpen().asScala)
        boxIdsToExclude.add(id.data)

    val outputs: JList[BoxData[Proposition, Box[Proposition]]] = new JArrayList()
    zenBoxDataList.foreach(element =>
      outputs.add(new ZenBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.publicKey)),
        element.value).asInstanceOf[BoxData[Proposition, Box[Proposition]]])
    )

    withdrawalRequestBoxDataList.foreach(element => {
      if(element.value < ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE))
        throw new IllegalArgumentException(s"Withdrawal transaction amount ${element.value} is below the MC dust threshold value: ${ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)}")

      outputs.add(new WithdrawalRequestBoxData(
        // Keep in mind that check MC rpc `getnewaddress` returns standard address with hash inside in LE
        // different to `getnewaddress "" true` hash that is in BE endianness.
        MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHorizenPublicKeyAddress(element.mainchainAddress, params)),
      element.value).asInstanceOf[BoxData[Proposition, Box[Proposition]]])
    })

    forgerBoxDataList.foreach{element =>
      val forgingBoxToAdd = new ForgerBoxData(
        PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.publicKey)),
        element.value,
        PublicKey25519PropositionSerializer.getSerializer.parseBytesAndCheck(BytesUtils.fromHexString(element.blockSignPublicKey.getOrElse(element.publicKey))),
        VrfPublicKeySerializer.getSerializer.parseBytes(BytesUtils.fromHexString(element.vrfPubKey))
      )

      outputs.add(forgingBoxToAdd.asInstanceOf[BoxData[Proposition, Box[Proposition]]])
    }


    val outputsTotalAmount: Long = outputs.asScala.map(boxData => boxData.value()).sum
    val inputsMinimumExpectedAmount: Long = outputsTotalAmount + fee
    var inputsTotalAmount: Long = 0L

    val boxes = ArrayBuffer[ZenBox]()
    breakable {
      for (box <- wallet.boxesOfType(classOf[ZenBox], boxIdsToExclude).asScala) {
        boxes.append(box.asInstanceOf[ZenBox])
        inputsTotalAmount += box.value()
        if (inputsTotalAmount >= inputsMinimumExpectedAmount)
          break
      }
    }


    if(inputsTotalAmount < inputsMinimumExpectedAmount)
      throw new IllegalArgumentException("Not enough balances in the wallet to create transaction.")

    // Add change if need.
    if(inputsTotalAmount > inputsMinimumExpectedAmount)
      outputs.add(new ZenBoxData(changeAddress, inputsTotalAmount - inputsMinimumExpectedAmount).asInstanceOf[BoxData[Proposition, Box[Proposition]]])

    // Create unsigned tx
    val boxIds = boxes.map(_.id()).asJava
    // Create a list of fake proofs for further messageToSign calculation
    val fakeProofs: JList[Proof[Proposition]] = Collections.nCopies(boxIds.size(), null)
    val unsignedTransaction = new SidechainCoreTransaction(boxIds, outputs, fakeProofs, fee, SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION)

    // Create signed tx.
    val messageToSign = unsignedTransaction.messageToSign()
    val proofs = boxes.map(box => {
      wallet.secretByPublicKey25519Proposition(box.proposition()).get().sign(messageToSign).asInstanceOf[Proof[Proposition]]
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
  private[api] case class ReqFindById(transactionId: String, blockHash: Option[String], format: Option[Boolean])

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
                                                automaticSend: Option[Boolean],
                                                format: Option[Boolean],
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
  private[api] case class ReqSpendForgingStake(transactionInputs: List[TransactionInput],
                                                      regularOutputs: List[TransactionOutput],
                                                      forgerOutputs: List[TransactionForgerOutput],
                                                      format: Option[Boolean]) {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(regularOutputs.nonEmpty || forgerOutputs.nonEmpty, "Empty outputs")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqOpenStake(transactionInput: TransactionInput,
                                       regularOutputProposition: String,
                                       forgerIndex: Int,
                                       format: Option[Boolean],
                                       automaticSend: Option[Boolean],
                                       @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(transactionInput != null, "Empty input")
    require(regularOutputProposition.nonEmpty, "Empty regularOutputProposition")
    require(forgerIndex >= 0, "Forger list index negative")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqOpenStakeSimplified(forgerProposition: String,
                                       forgerIndex: Int,
                                       format: Option[Boolean],
                                       automaticSend: Option[Boolean],
                                       @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(forgerProposition.nonEmpty, "Empty forgerProposition")
    require(forgerIndex >= 0, "Forger list index negative")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateKeyRotationTransaction(keyType: Int,
                                                          keyIndex: Int,
                                                          newKey: String,
                                                          signingKeySignature: String,
                                                          masterKeySignature: String,
                                                          newKeySignature: String,
                                                          format: Option[Boolean],
                                                          automaticSend: Option[Boolean],
                                                 @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Option[Long]) {
    require(keyIndex >= 0, "Key index negative")
    require(newKey.nonEmpty, "newKey is empty")
    require(signingKeySignature.nonEmpty, "signingKeySignature is empty")
    require(masterKeySignature.nonEmpty, "masterKeySignature is empty")
    require(newKeySignature.nonEmpty, "newKeySignature is empty")
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
