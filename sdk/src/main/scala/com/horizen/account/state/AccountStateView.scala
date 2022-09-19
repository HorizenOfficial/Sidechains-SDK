package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import com.horizen.account.receipt.{EthereumConsensusDataReceipt, EthereumReceipt}
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, ForgerStakeSmartContractAddress}
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.transaction.EthereumTransaction

import com.horizen.account.utils.{AccountBlockFeeInfo, MainchainTxCrosschainOutputAddressUtil, ZenWeiConverter}
import com.horizen.block.{MainchainBlockReferenceData, MainchainTxForwardTransferCrosschainOutput, MainchainTxSidechainCreationCrosschainOutput, WithdrawalEpochCertificate}
import com.horizen.consensus.{ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.{ResourceHandle, StateDB, StateStorageStrategy}
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.state.StateView
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import scorex.core.VersionTag
import scorex.util.ScorexLogging

import java.math.BigInteger
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.util.Try

class AccountStateView(
  metadataStorageView: AccountStateMetadataStorageView,
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

    refData.topQualityCertificate.foreach(cert => {
      log.debug(s"adding top quality cert to state: $cert.")
      addCertificate(cert)
    })

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
          val data = Bytes.concat(BytesUtils.fromHexString(AddNewStakeCmd), cmdInput.encode())

          val message = new Message(
            ownerAddressProposition,
            new AddressProposition(ForgerStakeSmartContractAddress),
            BigInteger.ZERO, // gasPrice
            BigInteger.ZERO, // gasFeeCap
            BigInteger.ZERO, // gasTipCap
            BigInteger.ZERO, // gasLimit
            stakedAmount,
            BigInteger.ONE.negate(), // a negative nonce value will rule out collision with real transactions
            data)

          val returnData = forgerStakesProvider.addScCreationForgerStake(message, this)
          log.debug(s"sc creation forging stake added with stakeid: ${BytesUtils.toHexString(returnData)}")

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

  override def getListOfForgerStakes: Seq[AccountForgingStakeInfo] =
    forgerStakesProvider.getListOfForgers(this)

  override def getForgerStakeData(stakeId: String): Option[ForgerStakeData] =
    forgerStakesProvider.findStakeData(this, BytesUtils.fromHexString(stakeId))

  def getOrderedForgingStakeInfoSeq: Seq[ForgingStakeInfo] = {
    forgerStakesProvider.getListOfForgers(this).map { item =>
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

  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def applyMessage(msg: Message, blockGasPool: GasPool, blockContext: BlockContext): Array[Byte] = {
    new StateTransition(this, messageProcessors, blockGasPool, blockContext).transition(msg)
  }

  /**
   * Possible outcomes:
   *  - tx applied succesfully => Receipt with status success
   *  - tx execution failed => Receipt with status failed
   *    - if any ExecutionFailedException was thrown, including but not limited to:
   *    - OutOfGasException (not intrinsic gas, see below!)
   *    - EvmException (EVM reverted) / fake contract exception
   *  - tx could not be applied => throws an exception (this will lead to an invalid block)
   *    - any of the preChecks fail
   *    - not enough gas for intrinsic gas
   *    - block gas limit reached
   */
  override def applyTransaction(tx: SidechainTypes#SCAT, txIndex: Int, blockGasPool: GasPool, blockContext: BlockContext): Try[EthereumConsensusDataReceipt] = Try {
    if (!tx.isInstanceOf[EthereumTransaction])
      throw new IllegalArgumentException(s"Unsupported transaction type ${tx.getClass.getName}")

    val ethTx = tx.asInstanceOf[EthereumTransaction]
    val txHash = BytesUtils.fromHexString(ethTx.id)
    val msg = ethTx.asMessage(blockContext.baseFee)

    // Tx context for stateDB, to know where to keep EvmLogs
    setupTxContext(txHash, txIndex)

    log.debug(s"applying msg: used pool gas ${blockGasPool.getUsedGas}")
    // apply message to state
    val status = try {
      applyMessage(msg, blockGasPool, blockContext)
      ReceiptStatus.SUCCESSFUL
    } catch {
      // any other exception will bubble up and invalidate the block
      case err: ExecutionFailedException =>
        log.error(s"applying message failed, tx.id=${ethTx.id}", err)
        ReceiptStatus.FAILED
    } finally {
      // finalize pending changes, clear the journal and reset refund counter
      stateDb.finalizeChanges()
    }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(
      ethTx.version(), status.id, blockGasPool.getUsedGas, getLogs(txHash))
    log.debug(s"Returning consensus data receipt: ${consensusDataReceipt.toString()}")
    log.debug(s"applied msg: used pool gas ${blockGasPool.getUsedGas}")

    consensusDataReceipt
  }

  override def isEoaAccount(address: Array[Byte]): Boolean = {
    stateDb.isEoaAccount(address)
  }

  override def isSmartContractAccount(address: Array[Byte]): Boolean = {
    stateDb.isSmartContractAccount(address)
  }

  override def accountExists(address: Array[Byte]): Boolean = {
    !stateDb.isEmpty(address)
  }

  // account modifiers:
  override def addAccount(address: Array[Byte], codeHash: Array[Byte]): Unit = {
    stateDb.setCodeHash(address, codeHash)
  }

  override def increaseNonce(address: Array[Byte]): Unit =
    stateDb.setNonce(address, getNonce(address).add(BigInteger.ONE))

  @throws(classOf[ExecutionFailedException])
  override def addBalance(address: Array[Byte], amount: BigInteger): Unit = {
    useGas(GasUtil.GasTBD)
    amount.compareTo(BigInteger.ZERO) match {
      case x if x == 0 => // amount is zero
      case x if x < 0 =>
        throw new ExecutionFailedException("cannot add negative amount to balance")
      case _ =>
        log.debug(s"Adding $amount to addr ${BytesUtils.toHexString(address)}")
        stateDb.addBalance(address, amount)
    }
  }

  @throws(classOf[ExecutionFailedException])
  override def subBalance(address: Array[Byte], amount: BigInteger): Unit = {
    useGas(GasUtil.GasTBD)
    // stateDb lib does not do any sanity check, and negative balances might arise (and java/go json IF does not correctly handle it)
    // TODO: for the time being do the checks here, later they will be done in the caller stack
    amount.compareTo(BigInteger.ZERO) match {
      case x if x == 0 => // amount is zero
      case x if x < 0 =>
        throw new ExecutionFailedException("cannot subtract negative amount from balance")
      case x if x > 0 && stateDb.getBalance(address).compareTo(amount) < 0 =>
        throw new ExecutionFailedException("insufficient balance")
      case _ =>
        log.debug(s"Subtracting $amount to addr ${BytesUtils.toHexString(address)}")
        stateDb.subBalance(address, amount)
    }
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    useGas(GasUtil.GasTBD)
    stateDb.getStorage(address, key, StateStorageStrategy.RAW)
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    useGas(GasUtil.GasTBD)
    stateDb.getStorage(address, key, StateStorageStrategy.CHUNKED)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    useGas(GasUtil.GasTBD)
    stateDb.setStorage(address, key, value, StateStorageStrategy.RAW)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    useGas(GasUtil.GasTBD)
    stateDb.setStorage(address, key, value, StateStorageStrategy.CHUNKED)
  }

  @throws(classOf[OutOfGasException])
  override def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Unit = {
    useGas(GasUtil.GasTBD)
    stateDb.removeStorage(address, key, StateStorageStrategy.RAW)
  }

  @throws(classOf[OutOfGasException])
  override def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Unit = {
    useGas(GasUtil.GasTBD)
    stateDb.removeStorage(address, key, StateStorageStrategy.CHUNKED)
  }

  // out-of-the-box helpers
  override def addCertificate(cert: WithdrawalEpochCertificate): Unit =
    metadataStorageView.updateTopQualityCertificate(cert)

  override def addFeeInfo(info: AccountBlockFeeInfo): Unit = {
    metadataStorageView.addFeePayment(info)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit =
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit =
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)

  override def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit =
    metadataStorageView.updateTransactionReceipts(receipts)

  def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] =
    metadataStorageView.getTransactionReceipt(txHash)

  def updateBaseFee(baseFee: BigInteger): Unit = metadataStorageView.updateBaseFee(baseFee)

  def baseFee: BigInteger = metadataStorageView.getBaseFee

  override def setCeased(): Unit = metadataStorageView.setCeased()

  override def commit(version: VersionTag): Try[Unit] = Try {
    // Update StateDB without version, then set the rootHash and commit metadataStorageView
    val rootHash = stateDb.commit()
    metadataStorageView.updateAccountStateRoot(rootHash)
    metadataStorageView.commit(version)
  }

  // getters
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest] =
    withdrawalReqProvider.getListOfWithdrawalReqRecords(withdrawalEpoch, this)

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long =
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch).map(_.quality).getOrElse(0)

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = metadataStorageView.getWithdrawalEpochInfo

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getFeePayments(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountBlockFeeInfo] = {
    var blockFeeInfoSeq = metadataStorageView.getFeePayments(withdrawalEpoch)
    blockToAppendFeeInfo.foreach(blockFeeInfo => blockFeeInfoSeq = blockFeeInfoSeq :+ blockFeeInfo)

    blockFeeInfoSeq
  }

  // account specific getters
  override def getNonce(address: Array[Byte]): BigInteger = {
    stateDb.getNonce(address)
  }

  override def getBalance(address: Array[Byte]): BigInteger = {
    stateDb.getBalance(address)
  }

  override def getCodeHash(address: Array[Byte]): Array[Byte] = {
    stateDb.getCodeHash(address)
  }

  override def getCode(address: Array[Byte]): Array[Byte] = {
    stateDb.getCode(address)
  }

  override def getAccountStateRoot: Array[Byte] = metadataStorageView.getAccountStateRoot

  override def getLogs(txHash: Array[Byte]): Array[EvmLog] = stateDb.getLogs(txHash)

  @throws(classOf[OutOfGasException])
  override def addLog(evmLog: EvmLog): Unit = {
    useGas(GasUtil.logGas(evmLog))
    stateDb.addLog(evmLog)
  }

  // when a method is called on a closed handle, LibEvm throws an exception
  override def close(): Unit = stateDb.close()

  override def getStateDbHandle: ResourceHandle = stateDb

  override def getIntermediateRoot: Array[Byte] = stateDb.getIntermediateRoot

  def getRefund: BigInteger = stateDb.getRefund

  def snapshot: Int = stateDb.snapshot()

  def revertToSnapshot(revisionId: Int): Unit = stateDb.revertToSnapshot(revisionId)

  // used automatic gas consumption
  private var trackedGasPool: Option[GasPool] = None

  def enableGasTracking(gasPool: GasPool): Unit = trackedGasPool = Some(gasPool)

  def disableGasTracking(): Unit = trackedGasPool = None

  @throws(classOf[OutOfGasException])
  private def useGas(gas: BigInteger): Unit = trackedGasPool match {
    case Some(gasPool) => gasPool.subGas(gas)
    case None => // gas tracking is disabled
  }
}
