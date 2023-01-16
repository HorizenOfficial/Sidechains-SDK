package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes
import com.horizen.account.api.http.AccountTransactionErrorResponse._
import com.horizen.account.api.http.AccountTransactionRestScheme._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS_BYTES
import com.horizen.account.utils.{EthereumTransactionDecoder, EthereumTransactionUtils, ZenWeiConverter}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.TransactionBaseErrorResponse.ErrorBadCircuit
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse, TransactionBaseApiRoute}
import com.horizen.certificatesubmitter.keys.{KeyRotationProof, KeyRotationProofTypes}
import com.horizen.cryptolibprovider.utils.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrSignatureSerializer
import com.horizen.proposition.{MCPublicKeyHashPropositionSerializer, PublicKey25519Proposition, SchnorrPropositionSerializer, VrfPublicKey}
import com.horizen.schnorrnative.SchnorrPublicKey
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import sparkz.core.settings.RESTApiSettings

import java.lang
import java.math.BigInteger
import java.util.{Optional => JOptional}
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class AccountTransactionApiRoute(override val settings: RESTApiSettings,
                                      sidechainNodeViewHolderRef: ActorRef,
                                      sidechainTransactionActorRef: ActorRef,
                                      companion: SidechainAccountTransactionsCompanion,
                                      params: NetworkParams,
                                      circuitType: CircuitTypes)
                                     (implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends TransactionBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView](sidechainTransactionActorRef, companion) with SidechainTypes {

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])


  override val route: Route = pathPrefix("transaction") {
    allTransactions ~ sendCoinsToAddress ~ createEIP1559Transaction ~ createLegacyTransaction ~ sendRawTransaction ~
      signTransaction ~ makeForgerStake ~ withdrawCoins ~ spendForgingStake ~ createSmartContract ~ allWithdrawalRequests ~
      allForgingStakes ~ myForgingStakes ~ decodeTransactionBytes ~ createKeyRotationTransaction
  }

  def getFittingSecret(nodeView: AccountNodeView, fromAddress: Option[String], txValueInWei: BigInteger)
  : Option[PrivateKeySecp256k1] = {

    val wallet = nodeView.getNodeWallet
    val allAccounts = wallet.secretsOfType(classOf[PrivateKeySecp256k1])

    val secret = allAccounts.find(
      a => (fromAddress.isEmpty ||
        BytesUtils.toHexString(a.asInstanceOf[PrivateKeySecp256k1].publicImage
          .address) == fromAddress.get) &&
        nodeView.getNodeState.getBalance(a.asInstanceOf[PrivateKeySecp256k1].publicImage.address).compareTo(txValueInWei) >= 0 // TODO account for gas
    )

    if (secret.nonEmpty) Option.apply(secret.get.asInstanceOf[PrivateKeySecp256k1])
    else Option.empty[PrivateKeySecp256k1]
  }

  def signTransactionWithSecret(secret: PrivateKeySecp256k1, tx: EthereumTransaction): EthereumTransaction = {
    val messageToSign = tx.messageToSign()
    val msgSignature = secret.sign(messageToSign)
    new EthereumTransaction(
        tx,
        new SignatureSecp256k1(msgSignature.getV, msgSignature.getR, msgSignature.getS)
    )
  }

  def signTransactionEIP155WithSecret(secret: PrivateKeySecp256k1, tx: EthereumTransaction): EthereumTransaction = {
    val messageToSign = tx.messageToSign()
    val msgSignature = secret.sign(messageToSign)
    new EthereumTransaction(
        tx,
        new SignatureSecp256k1(msgSignature.getV, msgSignature.getR, msgSignature.getS)
    )
  }

  /**
   * Create and sign a core transaction, specifying regular outputs and fee. Search for and spend proper amount of regular coins. Then validate and send the transaction.
   * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
   */
  def sendCoinsToAddress: Route = (post & path("sendCoinsToAddress")) {
    withAuth {
      entity(as[ReqSendCoinsToAddress]) { body =>
        // lock the view and try to create EvmTransaction
        applyOnNodeView { sidechainNodeView =>
          val valueInWei = ZenWeiConverter.convertZenniesToWei(body.value)
          val destAddress = body.to
          // TODO actual gas implementation
          var gasLimit = GasUtil.TxGas
          var gasPrice = sidechainNodeView.getNodeHistory.getBestBlock.header.baseFee

          if (body.gasInfo.isDefined) {
            gasPrice = body.gasInfo.get.maxFeePerGas
            gasLimit = body.gasInfo.get.gasLimit
          }

          // check if the fromAddress is either empty or it fits and the value is high enough
          val txCost = valueInWei.add(gasPrice.multiply(gasLimit))

          val secret = getFittingSecret(sidechainNodeView, body.from, txCost)
          val dataBytes = Array[Byte]()
          secret match {
            case Some(secret) =>
              val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
              val isEIP155 = body.EIP155.getOrElse(false)
              val response = if (isEIP155) {
                val tmpTx = new EthereumTransaction(
                  params.chainId,
                  EthereumTransactionUtils.getToAddressFromString(destAddress),
                  nonce,
                  gasPrice,
                  gasLimit,
                  valueInWei,
                  dataBytes,
                  null
                )
                validateAndSendTransaction(signTransactionEIP155WithSecret(secret, tmpTx))
              } else {
                val tmpTx = new EthereumTransaction(
                  EthereumTransactionUtils.getToAddressFromString(destAddress),
                  nonce,
                  gasPrice,
                  gasLimit,
                  valueInWei,
                  dataBytes,
                  null)
                validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
              }
              response
            case None =>
              ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
          }
        }
      }
    }
  }

  /**
   * Create and sign a core transaction, specifying regular outputs and fee. Search for and spend proper amount of regular coins. Then validate and send the transaction.
   * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
   */
  def createEIP1559Transaction: Route = (post & path("createEIP1559Transaction")) {
    withAuth {
      entity(as[ReqEIP1559Transaction]) { body =>
        // lock the view and try to create CoreTransaction
        applyOnNodeView { sidechainNodeView =>
          val tx_cost = body.value.add(body.gasLimit.multiply(body.maxFeePerGas))
          val secret = getFittingSecret(sidechainNodeView, body.from, tx_cost)

          val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.get.publicImage.address))

          var signedTx: EthereumTransaction = new EthereumTransaction(
            params.chainId,
            EthereumTransactionUtils.getToAddressFromString(body.to.orNull),
            nonce,
            body.gasLimit,
            body.maxPriorityFeePerGas,
            body.maxFeePerGas,
            body.value,
            EthereumTransactionUtils.getDataFromString(body.data),
            if (body.signature_v.isDefined)
              new SignatureSecp256k1(
                body.signature_v.get,
                body.signature_r.get,
                body.signature_s.get)
            else
              null
          )
          if (!signedTx.isSigned) {
            secret match {
              case Some(secret) =>
                signedTx = signTransactionWithSecret(secret, signedTx)
                validateAndSendTransaction(signedTx)
              case None =>
                ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
            }
          }
          else
            validateAndSendTransaction(signedTx)
        }
      }
    }
  }

  /**
   * Create a legacy evm transaction, specifying inputs.
   */
  def createLegacyTransaction: Route = (post & path("createLegacyTransaction")) {
    withAuth {
      entity(as[ReqLegacyTransaction]) { body =>
        // lock the view and try to send the tx
        applyOnNodeView { sidechainNodeView =>
          var signedTx = new EthereumTransaction(
            EthereumTransactionUtils.getToAddressFromString(body.to.orNull),
            body.nonce,
            body.gasPrice,
            body.gasLimit,
            body.value.orNull,
            EthereumTransactionUtils.getDataFromString(body.data),
            if (body.signature_v.isDefined)
              new SignatureSecp256k1(
                body.signature_v.get,
                body.signature_r.get,
                body.signature_s.get)
            else
              null
          )
          if (!signedTx.isSigned) {
            val txCost = signedTx.maxCost

            val secret =
              getFittingSecret(sidechainNodeView, body.from, txCost)
            secret match {
              case Some(secret) =>
                signedTx = signTransactionWithSecret(secret, signedTx)
                validateAndSendTransaction(signedTx)
              case None =>
                ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
            }
          }
          else
            validateAndSendTransaction(signedTx)
        }
      }
    }
  }

  /**
   * Create a raw evm transaction, specifying the bytes.
   */
  def sendRawTransaction: Route = (post & path("sendRawTransaction")) {
    withAuth {
      entity(as[ReqRawTransaction]) { body =>
        // lock the view and try to create CoreTransaction
        applyOnNodeView { sidechainNodeView =>
          var signedTx = EthereumTransactionDecoder.decode(body.payload)
          if (!signedTx.isSigned) {
            val txCost = signedTx.maxCost
            val secret =
              getFittingSecret(sidechainNodeView, body.from, txCost)
            secret match {
              case Some(secret) =>
                signedTx = signTransactionWithSecret(secret, signedTx)
                validateAndSendTransaction(signedTx)
              case None =>
                ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
            }
          }
          else
            validateAndSendTransaction(signedTx)
        }
      }
    }
  }

  def signTransaction: Route = (post & path("signTransaction")) {
    withAuth {
      entity(as[ReqRawTransaction]) {
        body => {
          applyOnNodeView { sidechainNodeView =>
            var signedTx = EthereumTransactionDecoder.decode(body.payload)
            val txCost = signedTx.maxCost
            val secret =
              getFittingSecret(sidechainNodeView, body.from, txCost)
            secret match {
              case Some(secret) =>
                signedTx = signTransactionWithSecret(secret, signedTx)
                ApiResponseUtil.toResponse(rawTransactionResponseRepresentation(signedTx))
              case None =>
                ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
            }
          }
        }
      }
    }
  }


  def makeForgerStake: Route = (post & path("makeForgerStake")) {
    withAuth {
      entity(as[ReqCreateForgerStake]) { body =>
        // lock the view and try to create CoreTransaction
        applyOnNodeView { sidechainNodeView =>
          val valueInWei = ZenWeiConverter.convertZenniesToWei(body.forgerStakeInfo.value)

          // default gas related params
          val baseFee = sidechainNodeView.getNodeHistory.getBestBlock.header.baseFee
          var maxPriorityFeePerGas = GasUtil.GasForgerStakeMaxPriorityFee
          var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
          var gasLimit = BigInteger.TWO.multiply(GasUtil.TxGas)

          if (body.gasInfo.isDefined) {
            maxFeePerGas = body.gasInfo.get.maxFeePerGas
            maxPriorityFeePerGas = body.gasInfo.get.maxPriorityFeePerGas
            gasLimit = body.gasInfo.get.gasLimit
          }

          //getFittingSecret needs to take into account also gas
          val txCost = valueInWei.add(maxFeePerGas.multiply(gasLimit))

          val secret = getFittingSecret(sidechainNodeView, None, txCost)

          secret match {
            case Some(secret) =>

              val to = BytesUtils.toHexString(FORGER_STAKE_SMART_CONTRACT_ADDRESS_BYTES)
              val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
              val dataBytes = encodeAddNewStakeCmdRequest(body.forgerStakeInfo)
              val tmpTx: EthereumTransaction = new EthereumTransaction(
                params.chainId,
                EthereumTransactionUtils.getToAddressFromString(to),
                nonce,
                gasLimit,
                maxPriorityFeePerGas,
                maxFeePerGas,
                valueInWei,
                dataBytes,
                null
              )
              validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
            case None =>
              ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
          }

        }
      }
    }
  }

  def spendForgingStake: Route = (post & path("spendForgingStake")) {
    withAuth {
      entity(as[ReqSpendForgingStake]) { body =>
        // lock the view and try to create CoreTransaction
        applyOnNodeView { sidechainNodeView =>
          val valueInWei = BigInteger.ZERO
          // default gas related params
          val baseFee = sidechainNodeView.getNodeHistory.getBestBlock.header.baseFee
          var maxPriorityFeePerGas = BigInteger.valueOf(120)
          var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
          var gasLimit = BigInteger.TWO.multiply(GasUtil.TxGas)

          if (body.gasInfo.isDefined) {
            maxFeePerGas = body.gasInfo.get.maxFeePerGas
            maxPriorityFeePerGas = body.gasInfo.get.maxPriorityFeePerGas
            gasLimit = body.gasInfo.get.gasLimit
          }
          //getFittingSecret needs to take into account only gas
          val txCost = valueInWei.add(maxFeePerGas.multiply(gasLimit))
          val secret = getFittingSecret(sidechainNodeView, None, txCost)
          secret match {
            case Some(txCreatorSecret) =>
              val to = BytesUtils.toHexString(FORGER_STAKE_SMART_CONTRACT_ADDRESS_BYTES)
              val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(txCreatorSecret.publicImage.address))
              val stakeDataOpt = sidechainNodeView.getNodeState.getForgerStakeData(body.stakeId)
              stakeDataOpt match {
                case Some(stakeData) =>
                  val stakeOwnerSecretOpt = sidechainNodeView.getNodeWallet.secretByPublicKey(stakeData.ownerPublicKey)
                  if (stakeOwnerSecretOpt.isEmpty) {
                    ApiResponseUtil.toResponse(ErrorForgerStakeOwnerNotFound(s"Forger Stake Owner not found"))
                  }
                  else {
                    val stakeOwnerSecret = stakeOwnerSecretOpt.get().asInstanceOf[PrivateKeySecp256k1]

                    val msgToSign = ForgerStakeMsgProcessor.getMessageToSign(BytesUtils.fromHexString(body.stakeId), txCreatorSecret.publicImage().address(), nonce.toByteArray)
                    val signature = stakeOwnerSecret.sign(msgToSign)
                    val dataBytes = encodeSpendStakeCmdRequest(signature, body.stakeId)
                    val tmpTx: EthereumTransaction = new EthereumTransaction(
                      params.chainId,
                      EthereumTransactionUtils.getToAddressFromString(to),
                      nonce,
                      gasLimit,
                      maxPriorityFeePerGas,
                      maxFeePerGas,
                      valueInWei,
                      dataBytes,
                      null
                    )

                    validateAndSendTransaction(signTransactionWithSecret(txCreatorSecret, tmpTx))
                  }
                case None => ApiResponseUtil.toResponse(ErrorForgerStakeNotFound(s"No Forger Stake found with stake id ${body.stakeId}"))
              }
            case None =>
              ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
          }

        }
      }
    }
  }

  def allForgingStakes: Route = (post & path("allForgingStakes")) {
    withNodeView { sidechainNodeView =>
      val accountState = sidechainNodeView.getNodeState
      val listOfForgerStakes = accountState.getListOfForgerStakes
      ApiResponseUtil.toResponse(RespForgerStakes(listOfForgerStakes.toList))
    }
  }

  def myForgingStakes: Route = (post & path("myForgingStakes")) {
    withAuth {
      withNodeView { sidechainNodeView =>
          val accountState = sidechainNodeView.getNodeState
          val listOfForgerStakes = accountState.getListOfForgerStakes

          if (listOfForgerStakes.nonEmpty) {
            val wallet = sidechainNodeView.getNodeWallet
            val walletPubKeys = wallet.allSecrets().map(_.publicImage).toSeq
            val ownedStakes = listOfForgerStakes.view.filter(stake => {
                walletPubKeys.contains(stake.forgerStakeData.ownerPublicKey)
            })
            ApiResponseUtil.toResponse(RespForgerStakes(ownedStakes.toList))
          } else {
            ApiResponseUtil.toResponse(RespForgerStakes(Seq().toList))
          }
        }

    }
  }

  def withdrawCoins: Route = (post & path("withdrawCoins")) {
    withAuth {
      entity(as[ReqWithdrawCoins]) { body =>
        // lock the view and try to create CoreTransaction
        applyOnNodeView { sidechainNodeView =>
          val to = BytesUtils.toHexString(WithdrawalMsgProcessor.contractAddress)
          val dataBytes = encodeAddNewWithdrawalRequestCmd(body.withdrawalRequest)
          val valueInWei = ZenWeiConverter.convertZenniesToWei(body.withdrawalRequest.value)
          val gasInfo = body.gasInfo

          // default gas related params
          val baseFee = sidechainNodeView.getNodeHistory.getBestBlock.header.baseFee
          var maxPriorityFeePerGas = BigInteger.valueOf(120)
          var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
          var gasLimit = BigInteger.TWO.multiply(GasUtil.TxGas)

          if (gasInfo.isDefined) {
            maxFeePerGas = gasInfo.get.maxFeePerGas
            maxPriorityFeePerGas = gasInfo.get.maxPriorityFeePerGas
            gasLimit = gasInfo.get.gasLimit
          }

          val txCost = valueInWei.add(maxFeePerGas.multiply(gasLimit))
          val secret = getFittingSecret(sidechainNodeView, None, txCost)
          secret match {
            case Some(secret) =>

              val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
              val tmpTx: EthereumTransaction = new EthereumTransaction(
                params.chainId,
                EthereumTransactionUtils.getToAddressFromString(to),
                nonce,
                gasLimit,
                maxPriorityFeePerGas,
                maxFeePerGas,
                valueInWei,
                dataBytes,
                null
              )
              validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
            case None =>
              ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
          }

        }
      }
    }
  }

  def allWithdrawalRequests: Route = (post & path("allWithdrawalRequests")) {
    entity(as[ReqAllWithdrawalRequests]) { body =>
      withNodeView { sidechainNodeView =>
        val accountState = sidechainNodeView.getNodeState
        val listOfWithdrawalRequests = accountState.withdrawalRequests(body.epochNum)
        ApiResponseUtil.toResponse(RespAllWithdrawalRequests(listOfWithdrawalRequests.toList))
      }
    }
  }

  def createSmartContract: Route = (post & path("createSmartContract")) {
    withAuth {
      entity(as[ReqCreateContract]) { body =>
        // lock the view and try to create CoreTransaction
        applyOnNodeView { sidechainNodeView =>
          val valueInWei = BigInteger.ZERO
          // TODO actual gas implementation
          var maxFeePerGas = BigInteger.ONE
          var maxPriorityFeePerGas = BigInteger.ONE
          var gasLimit = BigInteger.ONE
          if (body.gasInfo.isDefined) {
            maxFeePerGas = body.gasInfo.get.maxFeePerGas
            maxPriorityFeePerGas = body.gasInfo.get.maxPriorityFeePerGas
            gasLimit = body.gasInfo.get.gasLimit
          }

          val txCost = valueInWei.add(maxFeePerGas.multiply(gasLimit))
          val secret = getFittingSecret(sidechainNodeView, None, txCost)
          secret match {
            case Some(secret) =>
              val to = null
              val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
              val data = body.contractCode
              val tmpTx: EthereumTransaction = new EthereumTransaction(
                params.chainId,
                EthereumTransactionUtils.getToAddressFromString(to),
                nonce,
                gasLimit,
                maxPriorityFeePerGas,
                maxFeePerGas,
                valueInWei,
                EthereumTransactionUtils.getDataFromString(data),
                null
              )
              validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
            case None =>
              ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
          }

        }
      }
    }
  }

  def createKeyRotationTransaction: Route = (post & path("createKeyRotationTransaction")) {
    withAuth {
      entity(as[ReqCreateKeyRotationTransaction]) { body =>
        circuitType match {
          case NaiveThresholdSignatureCircuit =>
            ApiResponseUtil.toResponse(ErrorBadCircuit("The current circuit doesn't support key rotation transaction!", JOptional.empty()))
          case NaiveThresholdSignatureCircuitWithKeyRotation =>
            applyOnNodeView { sidechainNodeView =>
              val to = BytesUtils.toHexString(CertificateKeyRotationMsgProcessor.CertificateKeyRotationContractAddress)
              checkKeyRotationProofValidity(body)
              val data = encodeSubmitKeyRotationRequestCmd(body)
              val gasInfo = body.gasInfo

              // default gas related params
              val baseFee = sidechainNodeView.getNodeHistory.getBestBlock.header.baseFee
              var maxPriorityFeePerGas = BigInteger.valueOf(120)
              var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
              var gasLimit = BigInteger.TWO.multiply(GasUtil.TxGas)

              if (gasInfo.isDefined) {
                maxFeePerGas = gasInfo.get.maxFeePerGas
                maxPriorityFeePerGas = gasInfo.get.maxPriorityFeePerGas
                gasLimit = gasInfo.get.gasLimit
              }

              val txCost = maxFeePerGas.multiply(gasLimit)
              val secret = getFittingSecret(sidechainNodeView, None, txCost)
              secret match {
                case Some(secret) =>

                  val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
                  val tmpTx: EthereumTransaction = new EthereumTransaction(
                    params.chainId,
                    EthereumTransactionUtils.getToAddressFromString(to),
                    nonce,
                    gasLimit,
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                    BigInteger.ZERO,
                    EthereumTransactionUtils.getDataFromString(data),
                    null
                  )
                  validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
                case None =>
                  ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
              }
            }
        }
      }
    }
  }


  def encodeAddNewStakeCmdRequest(forgerStakeInfo: TransactionForgerOutput): Array[Byte] = {
    val blockSignPublicKey = new PublicKey25519Proposition(BytesUtils.fromHexString(forgerStakeInfo.blockSignPublicKey.getOrElse(forgerStakeInfo.ownerAddress)))
    val vrfPubKey = new VrfPublicKey(BytesUtils.fromHexString(forgerStakeInfo.vrfPubKey))
    val addForgerStakeInput = AddNewStakeCmdInput(ForgerPublicKeys(blockSignPublicKey, vrfPubKey), new AddressProposition(BytesUtils.fromHexString(forgerStakeInfo.ownerAddress)))

    Bytes.concat(BytesUtils.fromHexString(ForgerStakeMsgProcessor.AddNewStakeCmd), addForgerStakeInput.encode())
  }

  def encodeSpendStakeCmdRequest(signatureSecp256k1: SignatureSecp256k1, stakeId: String): Array[Byte] = {
    val spendForgerStakeInput = RemoveStakeCmdInput(BytesUtils.fromHexString(stakeId), signatureSecp256k1)
    Bytes.concat(BytesUtils.fromHexString(ForgerStakeMsgProcessor.RemoveStakeCmd), spendForgerStakeInput.encode())
  }

  def encodeAddNewWithdrawalRequestCmd(withdrawal: TransactionWithdrawalRequest): Array[Byte] = {
    // Keep in mind that check MC rpc `getnewaddress` returns standard address with hash inside in LE
    // different to `getnewaddress "" true` hash that is in BE endianness.
    val mcAddrHash = MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHorizenPublicKeyAddress(withdrawal.mainchainAddress, params))
    val addWithdrawalRequestInput = AddWithdrawalRequestCmdInput(mcAddrHash)
    Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig), addWithdrawalRequestInput.encode())
  }

  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val rawTransactionResponseRepresentation: EthereumTransaction => SuccessResponse = {
    transaction =>
      RawTransactionOutput("0x" + BytesUtils.toHexString(transaction.encode(transaction.isSigned))
      )
  }

  private def checkKeyRotationProofValidity(body: ReqCreateKeyRotationTransaction): Unit = {
    val index = body.keyIndex
    val keyType = body.keyType
    val newKey = SchnorrPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.newKey))
    val newKeySignature = SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.newKeySignature))
    if (index < 0 || index >= params.signersPublicKeys.length)
      throw new IllegalArgumentException(s"Key rotation proof - key index out for range: $index")

    if (keyType < 0 || keyType >= KeyRotationProofTypes.maxId)
      throw new IllegalArgumentException("Key type enumeration value should be valid!")

    val messageToSign: Array[Byte] = newKey.getHash

    if (!newKeySignature.isValid(newKey, messageToSign))
      throw new IllegalArgumentException(s"Key rotation proof - self signature is invalid: $index")
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
  private[api] case class RespAllWithdrawalRequests(listOfWR: List[WithdrawalRequest]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespForgerStakes(stakes: List[AccountForgingStakeInfo]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionWithdrawalRequest(mainchainAddress: String, @JsonDeserialize(contentAs = classOf[java.lang.Long]) value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionForgerOutput(ownerAddress: String, blockSignPublicKey: Option[String], vrfPubKey: String, value: Long)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class EIP1559GasInfo(gasLimit: BigInteger, maxPriorityFeePerGas: BigInteger, maxFeePerGas: BigInteger) {
    require(gasLimit.signum() > 0, "Gas limit can not be 0")
    require(maxPriorityFeePerGas.signum() > 0, "MaxPriorityFeePerGas must be greater than 0")
    require(maxFeePerGas.signum() > 0, "MaxFeePerGas must be greater than 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSendCoinsToAddress(from: Option[String],
                                                nonce: Option[BigInteger],
                                                to: String,
                                                @JsonDeserialize(contentAs = classOf[lang.Long]) value: Long,
                                                EIP155: Option[Boolean],
                                                gasInfo: Option[EIP1559GasInfo]
                                               ) {
    require(to.nonEmpty, "Empty destination address")
    require(value >= 0, "Negative value. Value must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqWithdrawCoins(nonce: Option[BigInteger],
                                           withdrawalRequest: TransactionWithdrawalRequest,
                                           gasInfo: Option[EIP1559GasInfo]) {
    require(withdrawalRequest != null, "Withdrawal request info must be provided")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqAllWithdrawalRequests(epochNum: Int) {
    require(epochNum >= 0, "Epoch number must be positive")
  }

  def encodeSubmitKeyRotationRequestCmd(request: ReqCreateKeyRotationTransaction): String = {
    val keyType = KeyRotationProofTypes(request.keyType)
    val index = request.keyIndex
    val newKey = SchnorrPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(request.newKey))
    val signingSignature = SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(request.signingKeySignature))
    val masterSignature = SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(request.masterKeySignature))
    val newKeySignature = SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(request.newKeySignature))
    val keyRotationProof = KeyRotationProof(
      keyType,
      index,
      newKey,
      signingSignature,
      masterSignature
    )
    BytesUtils.toHexString(Bytes.concat(
      BytesUtils.fromHexString(CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig),
      SubmitKeyRotationCmdInput(keyRotationProof, newKeySignature).encode()
    ))
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateForgerStake(
                                                nonce: Option[BigInteger],
                                                forgerStakeInfo: TransactionForgerOutput,
                                                gasInfo: Option[EIP1559GasInfo]
                                              ) {
    require(forgerStakeInfo != null, "Forger stake info must be provided")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateContract(
                                             nonce: Option[BigInteger],
                                             contractCode: String,
                                             gasInfo: Option[EIP1559GasInfo]) {
    require(contractCode.nonEmpty, "Contract code must be provided")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateKeyRotationTransaction(keyType: Int,
                                                          keyIndex: Int,
                                                          newKey: String,
                                                          signingKeySignature: String,
                                                          masterKeySignature: String,
                                                          newKeySignature: String,
                                                          nonce: Option[BigInteger],
                                                          gasInfo: Option[EIP1559GasInfo]) {
    require(keyIndex >= 0, "Key index negative")
    require(newKey.nonEmpty, "newKey is empty")
    require(signingKeySignature.nonEmpty, "signingKeySignature is empty")
    require(masterKeySignature.nonEmpty, "masterKeySignature is empty")
    require(newKeySignature.nonEmpty, "newKeySignature is empty")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionIdDTO(transactionId: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RawTransactionOutput(transactionData: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSpendForgingStake(
                                                nonce: Option[BigInteger],
                                                stakeId: String,
                                                gasInfo: Option[EIP1559GasInfo]) {
    require(stakeId.nonEmpty, "Signature data must be provided")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqEIP1559Transaction(
                                                 from: Option[String],
                                                 to: Option[String],
                                                 nonce: Option[BigInteger],
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
    require(maxPriorityFeePerGas.signum() > 0, "MaxPriorityFeePerGas must be greater than 0")
    require(maxFeePerGas.signum() > 0, "MaxFeePerGas must be greater than 0")
    require(to.isEmpty || to.get.length == 40 /* address length without prefix 0x */ , "to is not empty but has the wrong length - do not use a 0x prefix")
    require(from.isEmpty || from.get.length == 40 /* address length without prefix 0x */ , "from is not empty but has the wrong length - do not use a 0x prefix")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqLegacyTransaction(to: Option[String],
                                               from: Option[String],
                                               nonce: BigInteger,
                                               gasLimit: BigInteger,
                                               gasPrice: BigInteger,
                                               value: Option[BigInteger],
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
    require(to.isEmpty || to.get.length == 40 /* address length without prefix 0x */ , "to is not empty but has the wrong length - do not use a 0x prefix")
    require(from.isEmpty || from.get.length == 40 /* address length without prefix 0x */ , "from is not empty but has the wrong length - do not use a 0x prefix")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqRawTransaction(from: Option[String], payload: String)


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
    override val code: String = "0205"
  }

  case class ErrorForgerStakeNotFound(description: String) extends ErrorResponse {
    override val code: String = "0206"
    override val exception: JOptional[Throwable] = JOptional.empty()
  }

  case class ErrorForgerStakeOwnerNotFound(description: String) extends ErrorResponse {
    override val code: String = "0207"
    override val exception: JOptional[Throwable] = JOptional.empty()
  }

}
