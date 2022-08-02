package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes
import com.horizen.account.api.http.AccountTransactionErrorResponse._
import com.horizen.account.api.http.AccountTransactionRestScheme._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.transaction.{EthereumTransaction, EthereumTransactionSerializer}
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainTransactionActor.ReceivableMessages.BroadcastTransaction
import com.horizen.api.http.SidechainTransactionErrorResponse.GenericTransactionError
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiRoute, SuccessResponse}
import com.horizen.node.NodeWalletBase
import com.horizen.params.NetworkParams
import com.horizen.proposition.{MCPublicKeyHashPropositionSerializer, PublicKey25519Proposition, VrfPublicKey}
import com.horizen.serialization.Views
import com.horizen.transaction.Transaction
import com.horizen.utils.BytesUtils
import org.web3j.crypto.Sign.SignatureData
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionEncoder.createEip155SignatureData
import scorex.core.settings.RESTApiSettings

import java.lang
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
    allTransactions ~ sendCoinsToAddress ~ createEIP1559Transaction ~ createLegacyTransaction ~ sendRawTransaction ~
      signTransaction ~ makeForgerStake ~ withdrawCoins ~ spendForgingStake ~ createSmartContract ~ allWithdrawalRequests ~ allForgingStakes
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
        nodeView.getNodeState.getBalance(a.asInstanceOf[PrivateKeySecp256k1].publicImage.address).compareTo(txValueInWei) >= 0 // TODO account for gas
    )

    if (secret.nonEmpty) Option.apply(secret.get.asInstanceOf[PrivateKeySecp256k1])
    else Option.empty[PrivateKeySecp256k1]
  }

  def signTransactionWithSecret(secret: PrivateKeySecp256k1, tx: EthereumTransaction): EthereumTransaction = {
    val messageToSign = tx.messageToSign()
    val msgSignature = secret.sign(messageToSign)
    new EthereumTransaction(
      new SignedRawTransaction(
        tx.getTransaction.getTransaction,
        new SignatureData(msgSignature.getV, msgSignature.getR, msgSignature.getS)
      )
    )
  }

  def signTransactionEIP155WithSecret(secret: PrivateKeySecp256k1, tx: EthereumTransaction): EthereumTransaction = {
    val messageToSign = tx.messageToSign()
    val msgSignature = secret.sign(messageToSign)
    new EthereumTransaction(
      new SignedRawTransaction(
        tx.getTransaction.getTransaction,
        createEip155SignatureData(new SignatureData(msgSignature.getV, msgSignature.getR, msgSignature.getS), params.chainId)
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
            val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
            val isEIP155 = body.EIP155.getOrElse(false)

            val response = if (isEIP155) {
                val tmpTx = new EthereumTransaction(
                  destAddress,
                  nonce,
                  gasPrice,
                  gasLimit,
                  valueInWei,
                  "",
                  new SignatureData(BigInteger.valueOf(params.chainId).toByteArray, Array[Byte](0), Array[Byte](0)))
                validateAndSendTransaction(signTransactionEIP155WithSecret(secret, tmpTx))
              } else {
                val tmpTx = new EthereumTransaction(
                  destAddress,
                  nonce,
                  gasPrice,
                  gasLimit,
                  valueInWei,
                  "",
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

  /**
   * Create and sign a core transaction, specifying regular outputs and fee. Search for and spend proper amount of regular coins. Then validate and send the transaction.
   * Return the new transaction as a hex string if format = false, otherwise its JSON representation.
   */
  def createEIP1559Transaction: Route = (post & path("createEIP1559Transaction")) {
    entity(as[ReqEIP1559Transaction]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        var signedTx: EthereumTransaction = new EthereumTransaction(
          params.chainId,
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
          body.value.orNull,
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


  def makeForgerStake: Route = (post & path("makeForgerStake")) {
    entity(as[ReqCreateForgerStake]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        val valueInWei = ZenWeiConverter.convertZenniesToWei(body.forgerStakeInfo.value)

        // TODO actual gas implementation
        var maxFeePerGas = BigInteger.ONE
        var maxPriorityFeePerGas = BigInteger.ONE
        var gasLimit = BigInteger.ONE
        if (body.gasInfo.isDefined) {
          maxFeePerGas = body.gasInfo.get.maxFeePerGas
          maxPriorityFeePerGas = body.gasInfo.get.maxPriorityFeePerGas
          gasLimit = body.gasInfo.get.gasLimit
        }
        //TODO Probably getFittingSecret would need to take into account also gas
        val secret = getFittingSecret(sidechainNodeView, None, valueInWei)
        secret match {
          case Some(secret) =>

            val to = BytesUtils.toHexString(ForgerStakeMsgProcessor.ForgerStakeSmartContractAddress.address())
            val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
            val data = encodeAddNewStakeCmdRequest(body.forgerStakeInfo)
            val tmpTx: EthereumTransaction = new EthereumTransaction(
              params.chainId,
              to,
              nonce,
              gasLimit,
              maxPriorityFeePerGas,
              maxFeePerGas,
              valueInWei,
              data,
              null
            )
            validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
          case None =>
            ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
        }

      }
    }
  }

  def spendForgingStake: Route = (post & path("spendForgingStake")) {
    entity(as[ReqSpendForgingStake]) { body =>
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
        //TODO Probably getFittingSecret would need to take into account also gas
        val secret = getFittingSecret(sidechainNodeView, None, valueInWei)
        secret match {
          case Some(txCreatorSecret) =>
            val to = BytesUtils.toHexString(ForgerStakeMsgProcessor.ForgerStakeSmartContractAddress.address())
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
                  val data = encodeSpendStakeCmdRequest(signature, body.stakeId)
                  val tmpTx: EthereumTransaction = new EthereumTransaction(
                    params.chainId,
                    to,
                    nonce,
                    gasLimit,
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                    valueInWei,
                    data,
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

  def allForgingStakes: Route = (post & path("allForgingStakes")) {
    withNodeView { sidechainNodeView =>
      val accountState = sidechainNodeView.getNodeState
      val listOfWithdrawalRequests = accountState.getListOfForgerStakes
      ApiResponseUtil.toResponse(RespAllForgerStakes(listOfWithdrawalRequests.toList))
    }
  }


  def withdrawCoins: Route = (post & path("withdrawCoins")) {
    entity(as[ReqWithdrawCoins]) { body =>
      // lock the view and try to create CoreTransaction
      applyOnNodeView { sidechainNodeView =>
        val to = BytesUtils.toHexString(WithdrawalMsgProcessor.fakeSmartContractAddress.address())
        val data = encodeAddNewWithdrawalRequestCmd(body.withdrawalRequest)
        val valueInWei = ZenWeiConverter.convertZenniesToWei(body.withdrawalRequest.value)
        val gasInfo = body.gasInfo

        // TODO actual gas implementation
        var maxFeePerGas = BigInteger.ONE
        var maxPriorityFeePerGas = BigInteger.ONE
        var gasLimit = BigInteger.ONE
        if (gasInfo.isDefined) {
          maxFeePerGas = gasInfo.get.maxFeePerGas
          maxPriorityFeePerGas = gasInfo.get.maxPriorityFeePerGas
          gasLimit = gasInfo.get.gasLimit
        }
        //TODO Probably getFittingSecret would need to take into account also gas
        val secret = getFittingSecret(sidechainNodeView, None, valueInWei)
        secret match {
          case Some(secret) =>

            val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
            val tmpTx: EthereumTransaction = new EthereumTransaction(
              params.chainId,
              to,
              nonce,
              gasLimit,
              maxPriorityFeePerGas,
              maxFeePerGas,
              valueInWei,
              data,
              null
            )
            validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
          case None =>
            ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
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
        //TODO Probably getFittingSecret would need to take into account also gas
        val secret = getFittingSecret(sidechainNodeView, None, valueInWei)
        secret match {
          case Some(secret) =>
            val to = null
            val nonce = body.nonce.getOrElse(sidechainNodeView.getNodeState.getNonce(secret.publicImage.address))
            val data = body.contractCode
            val tmpTx: EthereumTransaction = new EthereumTransaction(
              params.chainId,
              to,
              nonce,
              gasLimit,
              maxPriorityFeePerGas,
              maxFeePerGas,
              valueInWei,
              data,
              null
            )
            validateAndSendTransaction(signTransactionWithSecret(secret, tmpTx))
          case None =>
            ApiResponseUtil.toResponse(ErrorInsufficientBalance("No account with enough balance found", JOptional.empty()))
        }

      }
    }
  }


  def encodeAddNewStakeCmdRequest(forgerStakeInfo: TransactionForgerOutput): String = {
    val blockSignPublicKey = new PublicKey25519Proposition(BytesUtils.fromHexString(forgerStakeInfo.blockSignPublicKey.getOrElse(forgerStakeInfo.ownerAddress)))
    val vrfPubKey = new VrfPublicKey(BytesUtils.fromHexString(forgerStakeInfo.vrfPubKey))
    val addForgerStakeInput = AddNewStakeCmdInput(ForgerPublicKeys(blockSignPublicKey, vrfPubKey), new AddressProposition(BytesUtils.fromHexString(forgerStakeInfo.ownerAddress)))
    val data = BytesUtils.toHexString(Bytes.concat(BytesUtils.fromHexString(ForgerStakeMsgProcessor.AddNewStakeCmd), addForgerStakeInput.encode()))
    data
  }

  def encodeSpendStakeCmdRequest(signatureSecp256k1: SignatureSecp256k1, stakeId: String): String = {
    val spendForgerStakeInput = RemoveStakeCmdInput(BytesUtils.fromHexString(stakeId), signatureSecp256k1)
    val data = BytesUtils.toHexString(Bytes.concat(BytesUtils.fromHexString(ForgerStakeMsgProcessor.RemoveStakeCmd), spendForgerStakeInput.encode()))
    data
  }


  def encodeAddNewWithdrawalRequestCmd(withdrawal: TransactionWithdrawalRequest): String = {
    // Keep in mind that check MC rpc `getnewaddress` returns standard address with hash inside in LE
    // different to `getnewaddress "" true` hash that is in BE endianness.
    val mcAddrHash = MCPublicKeyHashPropositionSerializer.getSerializer.parseBytes(BytesUtils.fromHorizenPublicKeyAddress(withdrawal.mainchainAddress, params))
    val addWithdrawalRequestInput = AddWithdrawalRequestCmdInput(mcAddrHash)
    val data = BytesUtils.toHexString(Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig), addWithdrawalRequestInput.encode()))
    data
  }


  //function which describes default transaction representation for answer after adding the transaction to a memory pool
  val defaultTransactionResponseRepresentation: (Transaction => SuccessResponse) = {
    transaction => TransactionIdDTO(transaction.id)
  }


  private def validateAndSendTransaction(transaction: SidechainTypes#SCAT,
                                         transactionResponseRepresentation: (SidechainTypes#SCAT => SuccessResponse) = defaultTransactionResponseRepresentation) = {

    val barrier = Await.result(
      sidechainTransactionActorRef ? BroadcastTransaction(transaction),
      settings.timeout).asInstanceOf[Future[Unit]]
    onComplete(barrier) {
      case Success(_) =>
        ApiResponseUtil.toResponse(transactionResponseRepresentation(transaction))
      case Failure(exp) =>
        ApiResponseUtil.toResponse(GenericTransactionError("GenericTransactionError", JOptional.of(exp)))
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
  private[api] case class RespAllWithdrawalRequests(listOfWR: List[WithdrawalRequest]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespAllForgerStakes(stakes: List[AccountForgingStakeInfo]) extends SuccessResponse

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
  private[api] case class ReqCreateCoreTransaction(transactionInputs: List[TransactionInput],
                                                   regularOutputs: List[TransactionOutput],
                                                   withdrawalRequests: List[TransactionWithdrawalRequest],
                                                   forgerOutputs: List[TransactionForgerOutput],
                                                   format: Option[Boolean]) {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(regularOutputs.nonEmpty || withdrawalRequests.nonEmpty || forgerOutputs.nonEmpty, "Empty outputs")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateCoreTransactionSimplified(regularOutputs: List[TransactionOutput],
                                                             withdrawalRequests: List[TransactionWithdrawalRequest],
                                                             forgerOutputs: List[TransactionForgerOutput],
                                                             @JsonDeserialize(contentAs = classOf[java.lang.Long]) fee: Long,
                                                             format: Option[Boolean]) {
    require(regularOutputs.nonEmpty || withdrawalRequests.nonEmpty || forgerOutputs.nonEmpty, "Empty outputs")
    require(fee >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSendCoinsToAddress(from: Option[String],
                                                nonce: Option[BigInteger],
                                                to: String,
                                                @JsonDeserialize(contentAs = classOf[lang.Long]) value: Long,
                                                EIP155: Option[Boolean]) {
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


  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateForgerStake(
                                                nonce: Option[BigInteger],
                                                forgerStakeInfo: TransactionForgerOutput,
                                                gasInfo: Option[EIP1559GasInfo]
                                              ) {
    require(forgerStakeInfo != null, "Forger stake info must be provided")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSendTransactionPost(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateContract(
                                             nonce: Option[BigInteger],
                                             contractCode: String,
                                             gasInfo: Option[EIP1559GasInfo]) {
    require(contractCode.nonEmpty, "Contract code must be provided")
  }


  @JsonView(Array(classOf[Views.Default]))
  private[api] case class TransactionIdDTO(transactionId: String) extends SuccessResponse

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
  private[api] case class ReqRawTransaction(from: Option[String], payload: Array[Byte])


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
