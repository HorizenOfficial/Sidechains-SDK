package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import com.horizen.account.receipt.{EthereumConsensusDataReceipt, EthereumReceipt}
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, ForgerStakeSmartContractAddress}
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.{MainchainTxCrosschainOutputAddressUtil, ZenWeiConverter}
import com.horizen.block.{MainchainBlockReferenceData, MainchainTxForwardTransferCrosschainOutput, MainchainTxSidechainCreationCrosschainOutput, WithdrawalEpochCertificate}
import com.horizen.consensus.{ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.{ResourceHandle, StateDB, StateStorageStrategy}
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.state.StateView
import com.horizen.transaction.exception.TransactionSemanticValidityException
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.utils.{BlockFeeInfo, BytesUtils, WithdrawalEpochInfo}
import scorex.core.VersionTag
import scorex.util.ScorexLogging

import java.math.BigInteger
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.util.Try

class AccountStateView(metadataStorageView: AccountStateMetadataStorageView,
                       stateDb: StateDB,
                       messageProcessors: Seq[MessageProcessor])
  extends StateView[SidechainTypes#SCAT]
    with BaseAccountStateView
    with AutoCloseable
    with ScorexLogging {

  lazy val withdrawalReqProvider: WithdrawalRequestProvider = messageProcessors.find(_.isInstanceOf[WithdrawalRequestProvider]).get.asInstanceOf[WithdrawalRequestProvider]
  lazy val forgerStakesProvider: ForgerStakesProvider = messageProcessors.find(_.isInstanceOf[ForgerStakesProvider]).get.asInstanceOf[ForgerStakesProvider]

  // modifiers
  override def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[Unit] = Try {
    refData.sidechainRelatedAggregatedTransaction.foreach(aggTx => {
      aggTx.mc2scTransactionsOutputs().asScala.map {
        case sc: SidechainCreation =>
          // While processing sidechain creation output:
          // 1. extract first forger stake info: block sign public key, vrf public key, owner address, stake amount
          // 2. store the stake info record in the forging fake smart contract storage
          val scOut: MainchainTxSidechainCreationCrosschainOutput = sc.getScCrOutput

          val stakedAmount = ZenWeiConverter.convertZenniesToWei(scOut.amount)

          val ownerAddressProposition = new AddressProposition(
            MainchainTxCrosschainOutputAddressUtil.getAccountAddress(scOut.address))

          // customData = vrf key | blockSignerKey
          val vrfPublicKey = new VrfPublicKey(scOut.customCreationData.take(VrfPublicKey.KEY_LENGTH))
          val blockSignerProposition = new PublicKey25519Proposition(scOut.customCreationData.slice(VrfPublicKey.KEY_LENGTH, VrfPublicKey.KEY_LENGTH + PublicKey25519Proposition.KEY_LENGTH))

          val cmdInput = AddNewStakeCmdInput(
            ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
            ownerAddressProposition
          )

          val data: Array[Byte] = Bytes.concat(
            BytesUtils.fromHexString(AddNewStakeCmd),
            cmdInput.encode())

          val message = new Message(
            ownerAddressProposition,
            ForgerStakeSmartContractAddress,
            BigInteger.ZERO, // gasPrice
            BigInteger.ZERO, // gasFeeCap
            BigInteger.ZERO, // gasTipCap
            BigInteger.ZERO, // gasLimit
            stakedAmount,
            BigInteger.ONE.negate(), // a negative nonce value will rule out collision with real transactions
            data)

          forgerStakesProvider.addScCreationForgerStake(message, this) match {
            case res: ExecutionFailed =>
              log.error(res.getReason.getMessage)
              throw new IllegalArgumentException(res.getReason)
            case res: InvalidMessage =>
              log.error(res.getReason.getMessage)
              throw new IllegalArgumentException(res.getReason)
            case res: ExecutionSucceeded =>
              log.debug(s"sc creation forging stake added with stakeid: ${BytesUtils.toHexString(res.returnData())}")
          }

        case ft: ForwardTransfer =>
          val ftOut: MainchainTxForwardTransferCrosschainOutput = ft.getFtOutput

          // we trust the MC that this is a valid amount
          val value = ZenWeiConverter.convertZenniesToWei(ftOut.amount)

          val recipientProposition = new AddressProposition(
            MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOut.propositionBytes))

          if (isEoaAccount(recipientProposition.address())) {
            // stateDb will implicitly create account if not existing yet
            addBalance(recipientProposition.address(), value)
            log.debug(s"added FT amount = $value to address=$recipientProposition")
          } else {
            log.warn(s"ignored FT to non-EOA account, amount = $value to address=$recipientProposition (the amount was effectively burned)")
            // TODO: we should return the amount back to mcReturnAddress instead of just burning it
          }
      }
    })
  }

  override def getListOfForgerStakes: Seq[AccountForgingStakeInfo] = {
    forgerStakesProvider.getListOfForgers(this)
  }

  override def getForgerStakeData(stakeId: String): Option[ForgerStakeData] = {
    forgerStakesProvider.findStakeData(this, BytesUtils.fromHexString(stakeId))
  }

  def getOrderedForgingStakeInfoSeq: Seq[ForgingStakeInfo] = {
    val forgerStakeList = forgerStakesProvider.getListOfForgers(this)

    forgerStakeList.map {
      item =>
        ForgingStakeInfo(
          item.forgerStakeData.forgerPublicKeys.blockSignPublicKey,
          item.forgerStakeData.forgerPublicKeys.vrfPublicKey,
          ZenWeiConverter.convertWeiToZennies(item.forgerStakeData.stakedAmount))
    }.sorted(Ordering[ForgingStakeInfo].reverse)
  }


  def setupTxContext(txHash: Array[Byte], idx: Integer): Unit = {
    // set context for the created events/logs assignment
    stateDb.setTxContext(txHash, idx)
  }

  private def preCheck(tx: EthereumTransaction): BigInteger = {
    // We are sure that transaction is semantically valid (so all the tx fields are valid)
    // and was successfully verified by ChainIdBlockSemanticValidator

    // cache it since it is not a real getter
    val txFromAddr = tx.getFrom

    // TODO this is checked also by EthereumTransaction.semanticValidity()
    // Check signature
    // TODO: add again later and check - message to sign seems to be false (?)
    if (!tx.getSignature.isValid(txFromAddr, tx.messageToSign()))
      throw new TransactionSemanticValidityException(s"Transaction ${tx.id} is invalid: signature is invalid")

    // Check that "from" is EOA address
    if (!isEoaAccount(txFromAddr.address()))
      throw new TransactionSemanticValidityException(s"Transaction ${tx.id} is invalid: from account is not EOA")

    // Check the nonce
    val stateNonce: BigInteger = getNonce(txFromAddr.address())
    val txNonce: BigInteger = tx.getNonce
    val result = stateNonce.compareTo(txNonce)
    if (result > 0) {
      throw new TransactionSemanticValidityException(s"Transaction ${tx.id} is invalid: tx nonce $txNonce is too low (state nonce is $stateNonce)")
    } else if (result < 0) {
      throw new TransactionSemanticValidityException(s"Transaction ${tx.id} is invalid: tx nonce $txNonce is too high (state nonce is $stateNonce)")
    }
    if (txNonce.add(BigInteger.ONE).compareTo(txNonce) < 0)
      throw new TransactionSemanticValidityException(s"Transaction ${tx.id} is invalid: nonce $txNonce reached the max value")

    // Check eip15159 fee relation
    if (tx.isEIP1559) {
      // TODO:  tx.getMaxFeePerGas().compareTo(block base fee) < 0 -> exception: max fee per gas less than block base fee"
    }

    // Check that from account has enough balance to pay max gas*price value
    // and reserve this amount.
    val bookedGasPrice: BigInteger = buyGas(tx)

    // Check that it is enough balance to pay after gas was bought.
    val txBalanceAfterGasPrepayment: BigInteger = getBalance(txFromAddr.address())
    if (txBalanceAfterGasPrepayment.compareTo(tx.getValue) < 0)
      throw new TransactionSemanticValidityException(s"Transaction ${tx.id} is invalid: not enough founds $txBalanceAfterGasPrepayment to pay ${tx.getValue}")

    bookedGasPrice
  }

  private def buyGas(tx: EthereumTransaction): BigInteger = {
    // TODO: implement gas prepayment strategy
    // calc max possible payment
    // subtract the balance
    // return the used value
    BigInteger.ZERO
  }

  def applyMessage(message: Message): Option[ExecutionResult] = {
    // TODO: some refactoring is required here, applyMessage should include:
    //  - buying gas
    //  - validations that the caller has enough funds for everything (gas and value transfer)
    //  - processing message
    //  - returning unused gas and refunds
    //  it should not contain some of the "preCheck" validations though like checking the nonce
    messageProcessors.find(_.canProcess(message, this)).map(_.process(message, this))
  }

  override def applyTransaction(tx: SidechainTypes#SCAT, txIndex: Int, prevCumGasUsed: BigInteger): Try[EthereumConsensusDataReceipt] = Try {
    if (!tx.isInstanceOf[EthereumTransaction])
      throw new IllegalArgumentException(s"Unsupported transaction type ${tx.getClass.getName}")

    val ethTx = tx.asInstanceOf[EthereumTransaction]
    val txHash = BytesUtils.fromHexString(ethTx.id)

    // Do the checks and prepay gas
    val bookedGasPrice: BigInteger = preCheck(ethTx)

    // Set Tx context for stateDB, to know where to keep EvmLogs
    setupTxContext(txHash, txIndex)

    val message: Message = Message.fromTransaction(ethTx)

    // Increase the nonce by 1
    increaseNonce(message.getFrom.address())

    // Create a snapshot to know where to rollback in case of Message processing failure
    val revisionId: Int = stateDb.snapshot()

    val consensusDataReceipt: EthereumConsensusDataReceipt = applyMessage(message) match {
      case Some(success: ExecutionSucceeded) =>
        val evmLogs = getLogs(txHash)
        val gasUsed = success.gasUsed()
        new EthereumConsensusDataReceipt(
          ethTx.version(), ReceiptStatus.SUCCESSFUL.id, prevCumGasUsed.add(gasUsed), evmLogs)


      case Some(failed: ExecutionFailed) =>
        val evmLogs = getLogs(txHash)
        stateDb.revertToSnapshot(revisionId)
        val gasUsed = failed.gasUsed()
        new EthereumConsensusDataReceipt(
          ethTx.version(), ReceiptStatus.FAILED.id, prevCumGasUsed.add(gasUsed), evmLogs)


      case Some(invalid: InvalidMessage) =>
        throw new Exception(s"Transaction ${ethTx.id} is invalid.", invalid.getReason)

      case None =>
        throw new IllegalArgumentException(s"Transaction ${ethTx.id} has no known processor.")
    }

    // todo: refund gas: bookedGasPrice - actualGasPrice
    log.debug(s"Returning consensus data receipt: ${consensusDataReceipt.toString()}")
    consensusDataReceipt
  }

  override def isEoaAccount(address: Array[Byte]): Boolean = stateDb.isEoaAccount(address)

  override def isSmartContractAccount(address: Array[Byte]): Boolean = stateDb.isSmartContractAccount(address)

  override def accountExists(address: Array[Byte]): Boolean = !stateDb.isEmpty(address)

  // account modifiers:
  override def addAccount(address: Array[Byte], codeHash: Array[Byte]): Try[Unit] = Try {
    stateDb.setCodeHash(address, codeHash)
  }

  override def addBalance(address: Array[Byte], amount: BigInteger): Try[Unit] = Try {
    stateDb.addBalance(address, amount)
  }

  override def subBalance(address: Array[Byte], amount: BigInteger): Try[Unit] = Try {
    // stateDb lib does not do any sanity check, and negative balances might arise (and java/go json IF does not correctly handle it)
    // TODO: for the time being do the checks here, later they will be done in the caller stack
    require(amount.compareTo(BigInteger.ZERO) >= 0)
    val balance = stateDb.getBalance(address)
    require(balance.compareTo(amount) >= 0)

    stateDb.subBalance(address, amount)
  }

  override def increaseNonce(address: Array[Byte]): Try[Unit] = Try {
    val currentNonce: BigInteger = getNonce(address)
    stateDb.setNonce(address, currentNonce.add(BigInteger.ONE))
  }

  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit] = Try {
    stateDb.setStorage(address, key, value, StateStorageStrategy.RAW)
  }

  override def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit] = Try {
    stateDb.setStorage(address, key, value, StateStorageStrategy.CHUNKED)
  }

  override def getAccountStorage(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]] = Try {
    stateDb.getStorage(address, key, StateStorageStrategy.RAW)
  }

  override def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]] = Try {
    stateDb.getStorage(address, key, StateStorageStrategy.CHUNKED)
  }

  override def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Try[Unit] = Try {
    stateDb.removeStorage(address, key, StateStorageStrategy.RAW)
  }

  override def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Try[Unit] = Try {
    stateDb.removeStorage(address, key, StateStorageStrategy.CHUNKED)
  }

  // log handling
  // def addLog(log: EvmLog) : Try[Unit] = ???

  // out-of-the-box helpers
  override def addCertificate(cert: WithdrawalEpochCertificate): Unit = {
    metadataStorageView.updateTopQualityCertificate(cert)
  }

  override def addFeeInfo(info: BlockFeeInfo): Unit = {
    metadataStorageView.addFeePayment(info)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit = {
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)
  }

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit = {
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)
  }

  override def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit = {
    metadataStorageView.updateTransactionReceipts(receipts)
  }

  def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] = {
    metadataStorageView.getTransactionReceipt(txHash)
  }

  override def setCeased(): Unit = {
    metadataStorageView.setCeased()
  }


  override def commit(version: VersionTag): Try[Unit] = Try {
    // Update StateDB without version, then set the rootHash and commit metadataStorageView
    val rootHash = stateDb.commit()
    metadataStorageView.updateAccountStateRoot(rootHash)
    metadataStorageView.commit(version)
  }

  // getters
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest] =
    withdrawalReqProvider.getListOfWithdrawalReqRecords(withdrawalEpoch, this)

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)
  }

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch) match {
      case Some(certificate) => certificate.quality
      case None => 0
    }
  }

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    metadataStorageView.getWithdrawalEpochInfo
  }

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getFeePayments(withdrawalEpoch: Int): Seq[BlockFeeInfo] = {
    metadataStorageView.getFeePayments(withdrawalEpoch)
  }

  override def getAccountStateRoot: Array[Byte] = metadataStorageView.getAccountStateRoot

  override def getHeight: Int = metadataStorageView.getHeight

  // account specific getters
  override def getBalance(address: Array[Byte]): BigInteger = {
    stateDb.getBalance(address)
  }

  override def getCodeHash(address: Array[Byte]): Array[Byte] = {
    stateDb.getCodeHash(address)
  }

  override def getNonce(address: Array[Byte]): BigInteger = {
    stateDb.getNonce(address)
  }

  override def getLogs(txHash: Array[Byte]): Array[EvmLog] = {
    stateDb.getLogs(txHash)
  }

  override def addLog(evmLog: EvmLog): Try[Unit] = {
    Try {
      stateDb.addLog(evmLog)
    }
  }

  override def close(): Unit = {
    // when a method is called on a closed handle, LibEvm throws an exception
    stateDb.close()
  }

  override def getStateDbHandle: ResourceHandle = stateDb

  override def getIntermediateRoot: Array[Byte] = stateDb.getIntermediateRoot

  override def getCode(address: Array[Byte]): Array[Byte] = stateDb.getCode(address)
}
