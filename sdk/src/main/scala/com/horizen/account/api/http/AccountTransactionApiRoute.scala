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
import com.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS
import com.horizen.account.utils.{EthereumTransactionUtils, ZenWeiConverter}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.TransactionBaseErrorResponse.{ErrorBadCircuit, ErrorByteTransactionParsing}
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse, TransactionBaseApiRoute}
import com.horizen.certificatesubmitter.keys.KeyRotationProofTypes.{MasterKeyRotationProofType, SigningKeyRotationProofType}
import com.horizen.certificatesubmitter.keys.{KeyRotationProof, KeyRotationProofTypes}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.cryptolibprovider.utils.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import com.horizen.evm.utils.Address
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.proof.{SchnorrSignatureSerializer, Signature25519}
import com.horizen.proposition.{MCPublicKeyHashPropositionSerializer, PublicKey25519Proposition, SchnorrPropositionSerializer, VrfPublicKey}
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import sparkz.core.settings.RESTApiSettings
import java.math.BigInteger
import java.util.{Optional => JOptional}
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

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
    allTransactions ~ createLegacyEIP155Transaction ~ createEIP1559Transaction ~ createLegacyTransaction ~ sendTransaction ~
      signTransaction ~ makeForgerStake ~ withdrawCoins ~ spendForgingStake ~ createSmartContract ~ allWithdrawalRequests ~
      allForgingStakes ~ myForgingStakes ~ decodeTransactionBytes ~ openForgerList ~ allowedForgerList ~ createKeyRotationTransaction
  }

  private def getFittingSecret(nodeView: AccountNodeView, fromAddress: Option[String], txValueInWei: BigInteger)
  : Option[PrivateKeySecp256k1] = {

    val wallet = nodeView.getNodeWallet
    val allAccounts = wallet.secretsOfType(classOf[PrivateKeySecp256k1])

    val secret = allAccounts.find(
      a => (fromAddress.isEmpty ||
        BytesUtils.toHexString(a.asInstanceOf[PrivateKeySecp256k1].publicImage.address.toBytes) == fromAddress.get) &&
        nodeView.getNodeState.getBalance(a.asInstanceOf[PrivateKeySecp256k1].publicImage.address)
          .compareTo(txValueInWei) >= 0 // TODO account for gas
    )

    if (secret.nonEmpty) Option.apply(secret.get.asInstanceOf[PrivateKeySecp256k1])
    else Option.empty[PrivateKeySecp256k1]
  }

  private def signTransactionWithSecret(secret: PrivateKeySecp256k1, tx: EthereumTransaction): EthereumTransaction = {
    val messageToSign = tx.messageToSign()
    val msgSignature = secret.sign(messageToSign)
    new EthereumTransaction(
        tx,
        new SignatureSecp256k1(msgSignature.getV, msgSignature.getR, msgSignature.getS)
    )
  }

  /**
   * Create an unsigned legacy eth transaction, and then:
   *  - if the optional input parameter 'outputRawBytes'=True is set in ReqLegacyTransaction just
   *    return the raw hex bytes representation
   *  - otherwise sign it and send the resulting tx to the network and return the transaction id
   */
  def createLegacyTransaction: Route = (post & path("createLegacyTransaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqLegacyTransaction]) { body =>
          val txCost = body.value.getOrElse(BigInteger.ZERO)
            .add(body.gasLimit.multiply(body.gasPrice))

          applyOnNodeView { sidechainNodeView =>
            val secret = getFittingSecret(sidechainNodeView, body.from, txCost)
            secret match {
              case Some(secret) =>
                // compute the nonce if not specified in the params
                val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))

                val unsignedTx = new EthereumTransaction(
                  EthereumTransactionUtils.getToAddressFromString(body.to.orNull),
                  nonce,
                  body.gasPrice,
                  body.gasLimit,
                  body.value.getOrElse(BigInteger.ZERO),
                  EthereumTransactionUtils.getDataFromString(body.data),
                  if (body.signature_v.isDefined)
                    new SignatureSecp256k1(
                      BytesUtils.fromHexString(body.signature_v.get),
                      BytesUtils.fromHexString(body.signature_r.get),
                      BytesUtils.fromHexString(body.signature_s.get))
                  else
                    null
                )
                val resp = if (body.outputRawBytes.getOrElse(false)) {
                  ApiResponseUtil.toResponse(rawTransactionResponseRepresentation(unsignedTx))
                } else {
                  val signedTx = signTransactionWithSecret(secret, unsignedTx)
                  validateAndSendTransaction(signedTx)
                }
                resp

              case None =>
                ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
            }
          }
        }
      }
    }
  }

  /**
   * Create an unsigned EIP155 (Simple replay attack protection) legacy eth transaction, and then:
   *  - if the optional input parameter 'outputRawBytes'=True is set in ReqLegacyTransaction just
   *    return the raw hex bytes representation
   *  - otherwise sign it and send the resulting tx to the network and return the transaction id
   */
  def createLegacyEIP155Transaction: Route = (post & path("createLegacyEIP155Transaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqLegacyTransaction]) { body =>
          val txCost = body.value.getOrElse(BigInteger.ZERO)
            .add(body.gasLimit.multiply(body.gasPrice))

          applyOnNodeView { sidechainNodeView =>

            val secret = getFittingSecret(sidechainNodeView, body.from, txCost)
            secret match {
              case Some(secret) =>
                // compute the nonce if not specified in the params
                val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))

              val unsignedTx = new EthereumTransaction(
                params.chainId,
                EthereumTransactionUtils.getToAddressFromString(body.to.orNull),
                nonce,
                body.gasPrice,
                body.gasLimit,
                body.value.getOrElse(BigInteger.ZERO),
                EthereumTransactionUtils.getDataFromString(body.data),
                if (body.signature_v.isDefined)
                  new SignatureSecp256k1(
                    BytesUtils.fromHexString(body.signature_v.get),
                    BytesUtils.fromHexString(body.signature_r.get),
                    BytesUtils.fromHexString(body.signature_s.get))
                else
                  null
              )
              val resp = if (body.outputRawBytes.getOrElse(false)) {
                ApiResponseUtil.toResponse(rawTransactionResponseRepresentation(unsignedTx))
              } else {
                val signedTx = signTransactionWithSecret(secret, unsignedTx)
                validateAndSendTransaction(signedTx)
              }
              resp

            case None =>
              ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
          }
        }
      }
    }
  }
  }

  /**
   * Create an unsigned EIP1559 eth transaction, and then:
   *  - if the optional input parameter 'outputRawBytes'=True is set in ReqLegacyTransaction just
   *    return the raw hex bytes representation
   *  - otherwise sign it and send the resulting tx to the network and return the transaction id
   */
  def createEIP1559Transaction: Route = (post & path("createEIP1559Transaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqEIP1559Transaction]) { body =>

          val txCost = body.value.getOrElse(BigInteger.ZERO)
            .add(body.gasLimit.multiply(body.maxFeePerGas))

          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val secret = getFittingSecret(sidechainNodeView, body.from, txCost)
            secret match {
              case Some(secret) =>
                val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))

                val unsignedTx: EthereumTransaction = new EthereumTransaction(
                  params.chainId,
                  EthereumTransactionUtils.getToAddressFromString(body.to.orNull),
                  nonce,
                  body.gasLimit,
                  body.maxPriorityFeePerGas,
                  body.maxFeePerGas,
                  body.value.getOrElse(BigInteger.ZERO),
                  EthereumTransactionUtils.getDataFromString(body.data),
                  if (body.signature_v.isDefined)
                    new SignatureSecp256k1(
                      BytesUtils.fromHexString(body.signature_v.get),
                      BytesUtils.fromHexString(body.signature_r.get),
                      BytesUtils.fromHexString(body.signature_s.get))
                  else
                    null)
                val resp = if (body.outputRawBytes.getOrElse(false)) {
                  ApiResponseUtil.toResponse(rawTransactionResponseRepresentation(unsignedTx))
                } else {
                  val signedTx = signTransactionWithSecret(secret, unsignedTx)
                  validateAndSendTransaction(signedTx)
                }
                resp

              case None =>
                ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
            }
          }
        }
      }
    }
  }

  /**
   * Decode the input raw eth transaction bytes into an obj, sign it using the input 'from' address
   * and return the resulting signed raw eth transaction bytes
   */
  def signTransaction: Route = (post & path("signTransaction")) {
    withBasicAuth {
      _ => {
        entity(as[ReqSignTransaction]) {
          body => {
            applyOnNodeView { sidechainNodeView =>
              val unsignedTxObj = Try {
                companion.parseBytes(BytesUtils.fromHexString(body.transactionBytes)).asInstanceOf[EthereumTransaction]
              }
              unsignedTxObj match {
                case Success(unsignedTx) =>
                  val txCost = unsignedTx.maxCost
                  val secret = getFittingSecret(sidechainNodeView, body.from, txCost)
                  secret match {
                    case Some(secret) =>
                      val signedTx = signTransactionWithSecret(secret, unsignedTx)
                      ApiResponseUtil.toResponse(rawTransactionResponseRepresentation(signedTx))
                    case None =>
                      ApiResponseUtil.toResponse(ErrorInsufficientBalance("ErrorInsufficientBalance", JOptional.empty()))
                  }
                case Failure(exception) =>
                  ApiResponseUtil.toResponse(ErrorByteTransactionParsing("ErrorByteTransactionParsing", JOptional.of(exception)))

              }
            }
          }
        }
      }
    }
  }

  // creates an eth transaction which makes a call to the forger stake native smart contract
  // expressing the vote for opening the restrict forgers list.
  // Analogous to the UTXO model createOpenStakeTransaction
  def openForgerList: Route = (post & path("openForgerList")) {
    withBasicAuth {
      _ => {
        entity(as[ReqOpenStakeForgerList]) { body =>

          // first of all reject the command if we do not have closed forger list
          if (!params.restrictForgers) {
            ApiResponseUtil.toResponse(ErrorOpenForgersList(
              s"The list of forger is not restricted (see configuration)",
              JOptional.empty()))

          } else {

          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val valueInWei = BigInteger.ZERO
            // default gas related params
            val baseFee = sidechainNodeView.getNodeState.getNextBaseFee
            var maxPriorityFeePerGas = BigInteger.valueOf(120)
            var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
            var gasLimit = BigInteger.valueOf(500000)

            if (body.gasInfo.isDefined) {
              maxFeePerGas = body.gasInfo.get.maxFeePerGas
              maxPriorityFeePerGas = body.gasInfo.get.maxPriorityFeePerGas
              gasLimit = body.gasInfo.get.gasLimit
            }

            val txCost = valueInWei.add(maxFeePerGas.multiply(gasLimit))

            getBlockSignSecret(body.forgerIndex, sidechainNodeView.getNodeWallet) match {

              case Failure(e) =>
                ApiResponseUtil.toResponse(ErrorOpenForgersList(
                  s"Could not get proposition for forgerIndex=${body.forgerIndex}",
                  JOptional.of(e)))

              case Success(blockSignSecret) =>


                val secret = getFittingSecret(sidechainNodeView, None, txCost)

                secret match {
                  case Some(txCreatorSecret) =>
                    val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(txCreatorSecret.publicImage.address))

                    val msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
                      body.forgerIndex, txCreatorSecret.publicImage().address(), nonce.toByteArray)

                    val signature = blockSignSecret.sign(msgToSign)

                    val dataBytes = encodeOpenStakeCmdRequest(body.forgerIndex, signature)
                    val tmpTx: EthereumTransaction = new EthereumTransaction(
                      params.chainId,
                      JOptional.of(new AddressProposition(FORGER_STAKE_SMART_CONTRACT_ADDRESS)),
                      nonce,
                      gasLimit,
                      maxPriorityFeePerGas,
                      maxFeePerGas,
                      valueInWei,
                      dataBytes,
                      null
                    )

                    validateAndSendTransaction(signTransactionWithSecret(txCreatorSecret, tmpTx))

                  case None =>
                    ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))

                }

              }
            }
          }
        }
      }
    }
  }

  private def getBlockSignSecret(forgerIndex: Int, wallet: NodeWalletBase): Try[PrivateKey25519] = Try {
    val blockSignProposition = Try[PublicKey25519Proposition] {
      params.allowedForgersList(forgerIndex)._1
    }
    blockSignProposition match {
      case scala.util.Failure(e) =>
        throw new IllegalArgumentException(s"Could not get proposition for forgerIndex=$forgerIndex; error: " + e.getMessage)
      case Success(prop) =>
        val secret = wallet.secretByPublicKey(prop)
        if (secret.isEmpty) {
          throw new IllegalArgumentException(s"Could not get secret in wallet for proposition for prop=$prop")
        } else {
          secret.get().asInstanceOf[PrivateKey25519]
        }
    }
  }

  def makeForgerStake: Route = (post & path("makeForgerStake")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateForgerStake]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val valueInWei = ZenWeiConverter.convertZenniesToWei(body.forgerStakeInfo.value)

            // default gas related params
            val baseFee = sidechainNodeView.getNodeState.getNextBaseFee
            var maxPriorityFeePerGas = BigInteger.valueOf(120)
            var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
            var gasLimit = BigInteger.valueOf(500000)

            if (body.gasInfo.isDefined) {
              maxFeePerGas = body.gasInfo.get.maxFeePerGas
              maxPriorityFeePerGas = body.gasInfo.get.maxPriorityFeePerGas
              gasLimit = body.gasInfo.get.gasLimit
            }

            val txCost = valueInWei.add(maxFeePerGas.multiply(gasLimit))

            val secret = getFittingSecret(sidechainNodeView, None, txCost)

            secret match {
              case Some(secret) =>

                val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
                val dataBytes = encodeAddNewStakeCmdRequest(body.forgerStakeInfo)
                val tmpTx: EthereumTransaction = new EthereumTransaction(
                  params.chainId,
                JOptional.of(new AddressProposition(FORGER_STAKE_SMART_CONTRACT_ADDRESS)),
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
  }

  def spendForgingStake: Route = (post & path("spendForgingStake")) {
    withBasicAuth {
      _ => {
        entity(as[ReqSpendForgingStake]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val valueInWei = BigInteger.ZERO
            // default gas related params
            val baseFee = sidechainNodeView.getNodeState.getNextBaseFee
            var maxPriorityFeePerGas = BigInteger.valueOf(120)
            var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
            var gasLimit = BigInteger.valueOf(500000)

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

                    val msgToSign = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(BytesUtils.fromHexString(body.stakeId), txCreatorSecret.publicImage().address(), nonce.toByteArray)
                    val signature = stakeOwnerSecret.sign(msgToSign)
                    val dataBytes = encodeSpendStakeCmdRequest(signature, body.stakeId)
                    val tmpTx: EthereumTransaction = new EthereumTransaction(
                      params.chainId,
                      JOptional.of(new AddressProposition(FORGER_STAKE_SMART_CONTRACT_ADDRESS)),
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
  }

  def allForgingStakes: Route = (post & path("allForgingStakes")) {
    withNodeView { sidechainNodeView =>
      val accountState = sidechainNodeView.getNodeState
      val listOfForgerStakes = accountState.getListOfForgersStakes
      ApiResponseUtil.toResponse(RespForgerStakes(listOfForgerStakes.toList))
    }
  }

  def allowedForgerList: Route = (post & path("allowedForgerList")) {
    if (params.restrictForgers) {
      // get the restrict list of forgers from configuration
      val allowedForgerKeysList : Seq[(PublicKey25519Proposition, VrfPublicKey)] = params.allowedForgersList

      withNodeView { sidechainNodeView =>
        val accountState = sidechainNodeView.getNodeState
        // get the list of indexes of allowed forgers (0 / 1 depending on the vote expressed so far)
        val forgersIndexList : Seq[Int] = accountState.getAllowedForgerList
        // join the two lists into one
        val resultList : Seq[(Int, (PublicKey25519Proposition, VrfPublicKey))] = forgersIndexList.zip(allowedForgerKeysList)
        // create the result
        val allowedForgerInfoSeq = resultList.map {
          entry => RespForgerInfo(entry._2._1, entry._2._2, entry._1)
        }
        ApiResponseUtil.toResponse(RespAllowedForgerList(allowedForgerInfoSeq.toList))
      }
    } else {
      ApiResponseUtil.toResponse(RespAllowedForgerList(Seq().toList))
    }

  }

  def myForgingStakes: Route = (post & path("myForgingStakes")) {
    withBasicAuth {
      _ => {
        withNodeView { sidechainNodeView =>
          val accountState = sidechainNodeView.getNodeState
          val listOfForgerStakes = accountState.getListOfForgersStakes

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
  }

  def withdrawCoins: Route = (post & path("withdrawCoins")) {
    withBasicAuth {
      _ => {
        entity(as[ReqWithdrawCoins]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val dataBytes = encodeAddNewWithdrawalRequestCmd(body.withdrawalRequest)
            val valueInWei = ZenWeiConverter.convertZenniesToWei(body.withdrawalRequest.value)
            val gasInfo = body.gasInfo

          // default gas related params
          val baseFee = sidechainNodeView.getNodeState.getNextBaseFee
          var maxPriorityFeePerGas = BigInteger.valueOf(120)
          var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
          var gasLimit = BigInteger.valueOf(500000)

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
                JOptional.of(new AddressProposition(WithdrawalMsgProcessor.contractAddress)),
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
  }

  def allWithdrawalRequests: Route = (post & path("allWithdrawalRequests")) {
    entity(as[ReqAllWithdrawalRequests]) { body =>
      withNodeView { sidechainNodeView =>
        val accountState = sidechainNodeView.getNodeState
        val listOfWithdrawalRequests = accountState.getWithdrawalRequests(body.epochNum)
        ApiResponseUtil.toResponse(RespAllWithdrawalRequests(listOfWithdrawalRequests.toList))
      }
    }
  }

  def createSmartContract: Route = (post & path("createSmartContract")) {
    withBasicAuth {
      _ => {
        entity(as[ReqCreateContract]) { body =>
          // lock the view and try to create CoreTransaction
          applyOnNodeView { sidechainNodeView =>
            val valueInWei = BigInteger.ZERO

            val baseFee = sidechainNodeView.getNodeState.getNextBaseFee
            var maxPriorityFeePerGas = GasUtil.TxGasContractCreation
            var maxFeePerGas = BigInteger.TWO.multiply(baseFee).add(maxPriorityFeePerGas)
            var gasLimit = BigInteger.valueOf(500000)

            if (body.gasInfo.isDefined) {
              maxFeePerGas = body.gasInfo.get.maxFeePerGas
              maxPriorityFeePerGas = body.gasInfo.get.maxPriorityFeePerGas
              gasLimit = body.gasInfo.get.gasLimit
            }

            val txCost = valueInWei.add(maxFeePerGas.multiply(gasLimit))
            val secret = getFittingSecret(sidechainNodeView, None, txCost)
            secret match {
              case Some(secret) =>
                val to: String = null
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

                checkKeyRotationProofValidity(body, sidechainNodeView.getNodeState.getWithdrawalEpochInfo.epoch) match {
                  case Some(errorResponse) => ApiResponseUtil.toResponse(errorResponse)
                  case None =>

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
                          JOptional.of(new AddressProposition(CertificateKeyRotationMsgProcessor.CertificateKeyRotationContractAddress)),
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
    }
  }


  def encodeAddNewStakeCmdRequest(forgerStakeInfo: TransactionForgerOutput): Array[Byte] = {
    val blockSignPublicKey = new PublicKey25519Proposition(BytesUtils.fromHexString(forgerStakeInfo.blockSignPublicKey.getOrElse(forgerStakeInfo.ownerAddress)))
    val vrfPubKey = new VrfPublicKey(BytesUtils.fromHexString(forgerStakeInfo.vrfPubKey))
    val addForgerStakeInput = AddNewStakeCmdInput(ForgerPublicKeys(blockSignPublicKey, vrfPubKey), new Address("0x" + forgerStakeInfo.ownerAddress))

    Bytes.concat(BytesUtils.fromHexString(ForgerStakeMsgProcessor.AddNewStakeCmd), addForgerStakeInput.encode())
  }

  def encodeOpenStakeCmdRequest(forgerIndex: Int, signature: Signature25519): Array[Byte] = {
    val openStakeForgerListInput = OpenStakeForgerListCmdInput(forgerIndex, signature)
    Bytes.concat(BytesUtils.fromHexString(ForgerStakeMsgProcessor.OpenStakeForgerListCmd),
      openStakeForgerListInput.encode())
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

  private def checkKeyRotationProofValidity(body: ReqCreateKeyRotationTransaction, epoch: Int): Option[ErrorResponse] = {
    val index = body.keyIndex
    val keyType = body.keyType
    val newKey = SchnorrPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.newKey))
    val newKeySignature = SchnorrSignatureSerializer.getSerializer.parseBytes(BytesUtils.fromHexString(body.newKeySignature))
    if (index < 0 || index >= params.signersPublicKeys.length)
      return Some(ErrorInvalidKeyRotationProof(s"Key rotation proof - key index out of range: $index"))

    if (keyType < 0 || keyType >= KeyRotationProofTypes.maxId)
      return Some(ErrorInvalidKeyRotationProof(s"Key rotation proof - key type enumeration value invalid: $keyType"))

    val messageToSign = KeyRotationProofTypes(keyType) match {
      case SigningKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForSigningKeyUpdate(newKey.pubKeyBytes(), epoch, params.sidechainId)
      case MasterKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForMasterKeyUpdate(newKey.pubKeyBytes(), epoch, params.sidechainId)
    }

    if (!newKeySignature.isValid(newKey, messageToSign))
      return Some(ErrorInvalidKeyRotationProof(s"Key rotation proof - self signature is invalid: $index"))
    None
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
  private [api] case class RespForgerInfo(
                                 blockSign: PublicKey25519Proposition,
                                 vrfPubKey: VrfPublicKey,
                                 openForgersVote: Int)
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllowedForgerList(allowedForgers: List[RespForgerInfo]) extends SuccessResponse

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
  private[api] case class ReqOpenStakeForgerList(
                                                nonce: Option[BigInteger],
                                                forgerIndex: Int,
                                                gasInfo: Option[EIP1559GasInfo]) {
    require (forgerIndex >=0, "Forger index must be non negative")
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
  private[api] case class ReqSpendForgingStake(
                                                nonce: Option[BigInteger],
                                                stakeId: String,
                                                gasInfo: Option[EIP1559GasInfo]) {
    require(stakeId.nonEmpty, "Signature data must be provided")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqEIP1559Transaction(
                                                 to: Option[String],
                                                 from: Option[String],
                                                 nonce: Option[BigInteger],
                                                 gasLimit: BigInteger,
                                                 maxPriorityFeePerGas: BigInteger,
                                                 maxFeePerGas: BigInteger,
                                                 value: Option[BigInteger],
                                                 data: String,
                                                 signature_v: Option[String] = None,
                                                 signature_r: Option[String] = None,
                                                 signature_s: Option[String] = None,
                                                 outputRawBytes: Option[Boolean] = None) {
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
  private[api] case class ReqLegacyTransaction(
                                                to: Option[String],
                                                from: Option[String],
                                                nonce: Option[BigInteger],
                                                gasLimit: BigInteger,
                                                gasPrice: BigInteger,
                                                value: Option[BigInteger],
                                                data: String,
                                                signature_v: Option[String] = None,
                                                signature_r: Option[String] = None,
                                                signature_s: Option[String] = None,
                                                outputRawBytes: Option[Boolean] = None) {
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
  private[api] case class ReqSignTransaction(from: Option[String], transactionBytes: String)


}

object AccountTransactionErrorResponse {

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

  case class ErrorOpenForgersList(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0208"
  }

  case class ErrorInvalidKeyRotationProof(description: String) extends ErrorResponse {
    override val code: String = "0209"
    override val exception: JOptional[Throwable] = JOptional.empty()
  }

}
