package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.node.NodeAccountState
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.storage.AccountStateMetadataStorage
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.Secp256k1.generateContractAddress
import com.horizen.account.utils.{AccountBlockFeeInfo, AccountFeePaymentsUtils, AccountPayment, FeeUtils}
import com.horizen.account.validation.InvalidTransactionChainIdException
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.certnative.BackwardTransfer
import com.horizen.consensus.{ConsensusEpochInfo, ConsensusEpochNumber, ForgingStakeInfo, intToConsensusEpochNumber}
import com.horizen.cryptolibprovider.utils.CircuitTypes.NaiveThresholdSignatureCircuit
import com.horizen.evm._
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.params.NetworkParams
import com.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash, Sc2ScConfigurator}
import com.horizen.state.State
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ClosableResourceHandler, MerkleTree, TimeToEpochUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import sparkz.core._
import sparkz.core.transaction.state.TransactionValidation
import sparkz.core.utils.NetworkTimeProvider
import sparkz.util.{ModifierId, SparkzLogging, bytesToId}

import java.math.BigInteger
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class AccountState(
    val params: NetworkParams,
    val sc2scConfig : Sc2ScConfigurator,
    timeProvider: NetworkTimeProvider,
    blockHashProvider: HistoryBlockHashProvider,
    override val version: VersionTag,
    stateMetadataStorage: AccountStateMetadataStorage,
    stateDbStorage: Database,
    messageProcessors: Seq[MessageProcessor]
) extends State[SidechainTypes#SCAT, AccountBlock, AccountStateView, AccountState]
      with TransactionValidation[SidechainTypes#SCAT]
      with NodeAccountState
      with ClosableResourceHandler
      with SparkzLogging {

  override type NVCT = AccountState

  // Execute MessageProcessors initialization phase
  // Used once on genesis AccountState creation
  private def initProcessors(initialVersion: VersionTag): Try[AccountState] = Try {
    using(getView) { view =>
      for (processor <- messageProcessors) {
        processor.init(view)
      }

      try {
        view.commit(initialVersion)
      } catch {
        case t: Throwable =>
          val errMsg = s"Could not commit view with initial version: $initialVersion"
          log.error(errMsg, t)
          throw t
      }
      this
    }
  }

  // Modifiers:
  override def applyModifier(mod: AccountBlock): Try[AccountState] = Try {
    require(
      versionToBytes(version).sameElements(idToBytes(mod.parentId)),
      s"Incorrect state version!: ${mod.parentId} found, " + s"$version expected"
    )

    using(getView) { stateView =>
      if (stateView.hasCeased) {
        val errMsg = s"Can't apply Block ${mod.id}, because the sidechain has ceased."
        log.error(errMsg)
        throw new IllegalStateException(errMsg)
      }

      // Check Txs semantic validity first
      for (tx <- mod.sidechainTransactions)
        tx.semanticValidity()

      // TODO: keep McBlockRef validation in a view style, so in the applyMainchainBlockReferenceData method
      // Validate top quality certificate in the end of the submission window:
      // Reject block if it refers to the chain that conflicts with the top quality certificate content
      // Mark sidechain as ceased in case there is no certificate appeared within the submission window.
      val currentWithdrawalEpochInfo = getWithdrawalEpochInfo
      val modWithdrawalEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(mod, currentWithdrawalEpochInfo, params)

        // Check top quality certificate or notify that sidechain has ceased since we have no certificate in the end of the submission window.
        if(params.isNonCeasing) {
          // For non-ceasing sidechains certificate must be validated just when it has been received.
          // In case of multiple certificates appeared and at least one of them is invalid (conflicts with the current chain)
          // then the whole block is invalid.
          mod.mainchainBlockReferencesData.flatMap(_.topQualityCertificate).foreach(cert => validateTopQualityCertificate(cert, stateView))

          // Save the scTxCommitmentTreeRootHash of every mainchain header in a block
          mod.mainchainHeaders.foreach(mcHeader => stateView.addSidechainTxCommitmentTreeRootHash(mcHeader.hashScTxsCommitment))
        } else {
          // For ceasing sidechains submission window concept is used.
          // If SC block has reached the certificate submission window end -> check the top quality certificate
          // Note: even if mod contains multiple McBlockRefData entries, we are sure they belongs to the same withdrawal epoch.
          if (WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(mod, currentWithdrawalEpochInfo, params)) {
            val certReferencedEpochNumber = modWithdrawalEpochInfo.epoch - 1

            // Top quality certificate may present in the current SC block or in the previous blocks or can be absent.
            val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = mod.topQualityCertificateOpt.orElse(
              stateView.getTopQualityCertificate(certReferencedEpochNumber))

            // Check top quality certificate or notify that sidechain has ceased since we have no certificate in the end of the submission window.
            topQualityCertificateOpt match {
              case Some(cert) =>
                validateTopQualityCertificate(cert, stateView)
              case None =>
                log.info(s"In the end of the certificate submission window of epoch ${modWithdrawalEpochInfo.epoch} " +
                  s"there are no certificates referenced to the epoch $certReferencedEpochNumber. Sidechain has ceased.")
                stateView.setCeased()
            }
        }
      }

      // Update view with the block info
      stateView.updateWithdrawalEpochInfo(modWithdrawalEpochInfo)

      val consensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, mod.timestamp)
      stateView.updateConsensusEpochNumber(consensusEpochNumber)

      for (mcBlockRefData <- mod.mainchainBlockReferencesData) {
        stateView.addTopQualityCertificates(mcBlockRefData, mod.id)
        stateView.applyMainchainBlockReferenceData(mcBlockRefData)
      }

      // get also list of receipts, useful for computing the receiptRoot hash
      val receiptList = new ListBuffer[EthereumReceipt]()
      val blockNumber = stateMetadataStorage.getHeight + 1
      val blockHash = idToBytes(mod.id)

      var cumGasUsed: BigInteger = BigInteger.ZERO
      var cumBaseFee: BigInteger = BigInteger.ZERO // cumulative base-fee, burned in eth, goes to forgers pool
      var cumForgerTips: BigInteger = BigInteger.ZERO // cumulative max-priority-fee, is paid to block forger

      val blockGasPool = new GasPool(mod.header.gasLimit)
      val blockContext = new BlockContext(
        mod.header,
        blockNumber,
        consensusEpochNumber,
        modWithdrawalEpochInfo.epoch,
        params.chainId,
        blockHashProvider
      )

      for ((tx, txIndex) <- mod.sidechainTransactions.zipWithIndex) {
        stateView.applyTransaction(tx, txIndex, blockGasPool, blockContext) match {
          case Success(consensusDataReceipt) =>
            val txGasUsed = consensusDataReceipt.cumulativeGasUsed.subtract(cumGasUsed)
            // update cumulative gas used so far
            cumGasUsed = consensusDataReceipt.cumulativeGasUsed
            val ethTx = tx.asInstanceOf[EthereumTransaction]

            val txHash = BytesUtils.fromHexString(ethTx.id)

            // The contract address created, if the transaction was a contract creation
            val contractAddress = if (ethTx.getTo.isEmpty) {
              // this w3j util method is equivalent to the createAddress() in geth triggered also by CREATE opcode.
              // Note: geth has also a CREATE2 opcode which may be optionally used in a smart contract solidity implementation
              // in order to deploy another (deeper) smart contract with an address that is pre-determined before deploying it.
              // This does not impact our case since the CREATE2 result would not be part of the receipt.
              Option(generateContractAddress(ethTx.getFrom.address, ethTx.getNonce))
            } else {
              // otherwise nothing
              None
            }

            // get a receipt obj with non consensus data (logs updated too)
            val fullReceipt =
              EthereumReceipt(consensusDataReceipt, txHash, txIndex, blockHash, blockNumber, txGasUsed, contractAddress)

            log.debug(s"Adding to receipt list: ${fullReceipt.toString()}")

            receiptList += fullReceipt

            val (txBaseFeePerGas, txForgerTipPerGas) = GasUtil.getTxFeesPerGas(ethTx, blockContext.baseFee)
            cumBaseFee = cumBaseFee.add(txBaseFeePerGas.multiply(txGasUsed))
            cumForgerTips = cumForgerTips.add(txForgerTipPerGas.multiply(txGasUsed))

          case Failure(err: GasLimitReached) =>
            log.error("Could not apply tx, block gas limit exceeded")
            throw new IllegalArgumentException("Could not apply tx, block gas limit exceeded", err)

          case Failure(err) =>
            log.error("Could not apply tx", err)
            throw new IllegalArgumentException(err)
        }
      }

      log.debug(s"cumBaseFee=$cumBaseFee, cumForgerTips=$cumForgerTips")

      // The two contributions will go like this:
      // - base -> forgers pool, weighted by number of blocks forged
      // - tip -> block forger
      // Note: store also entries with zero values, which can arise in sc blocks without any tx
      stateView.updateFeePaymentInfo(AccountBlockFeeInfo(cumBaseFee, cumForgerTips, mod.header.forgerAddress))

      // If SC block has reached the end of the withdrawal epoch reward the forgers.
      evalForgersReward(mod, modWithdrawalEpochInfo, stateView)

      // check logs bloom consistency with block header
      mod.verifyLogsBloomConsistency(receiptList)

      // check stateRoot and receiptRoot against block header
      mod.verifyReceiptDataConsistency(receiptList.map(_.consensusDataReceipt))

      val stateRoot = stateView.getIntermediateRoot
      mod.verifyStateRootDataConsistency(stateRoot)

      // eventually, store full receipts in the metaDataStorage indexed by txid
      stateView.updateTransactionReceipts(receiptList)

      // update next base fee
      stateView.updateNextBaseFee(FeeUtils.calculateNextBaseFee(mod))

      stateView.commit(idToVersion(mod.id))

      new AccountState(
        params,
        sc2scConfig,
        timeProvider,
        blockHashProvider,
        idToVersion(mod.id),
        stateMetadataStorage,
        stateDbStorage,
        messageProcessors
      )
    }
  }


  private def evalForgersReward(mod: AccountBlock, modWithdrawalEpochInfo: WithdrawalEpochInfo, stateView: AccountStateView): Unit = {
    // If SC block has reached the end of the withdrawal epoch -> fee payments expected to be produced.
    // If SC block is in the middle of the withdrawal epoch -> no fee payments hash expected to be defined.
    val isWithdrawalEpochFinished: Boolean = WithdrawalEpochUtils.isEpochLastIndex(modWithdrawalEpochInfo, params)
    if (isWithdrawalEpochFinished) {
      // current block fee info is already in the view therefore we pass None as second param
      val feePayments = stateView.getFeePaymentsInfo(modWithdrawalEpochInfo.epoch, None)

      // Verify that Forger assumed the same fees to be paid as the current node does.
      val feePaymentsHash: Array[Byte] = AccountFeePaymentsUtils.calculateFeePaymentsHash(feePayments)

      if (!mod.feePaymentsHash.sameElements(feePaymentsHash)) {
        val errMsg = s"Block ${mod.id}: computed feePaymentsHash ${BytesUtils.toHexString(feePaymentsHash)} is different from the one in the block"
        log.error(errMsg)
        throw new IllegalArgumentException(errMsg)
      }

      // add rewards to forgers balance
      feePayments.foreach(payment => stateView.addBalance(payment.address.address(), payment.value))

    } else {
      // No fee payments expected
      if (!mod.feePaymentsHash.sameElements(AccountFeePaymentsUtils.DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH)) {
        val errMsg = s"Block ${mod.id} has feePaymentsHash ${BytesUtils.toHexString(mod.feePaymentsHash)} defined when no fee payments expected."
        throw new IllegalArgumentException(errMsg)
      }
    }
  }

  private def validateTopQualityCertificate(
      topQualityCertificate: WithdrawalEpochCertificate,
      stateView: AccountStateView
  ): Unit = {

    val certReferencedEpochNumber: Int = topQualityCertificate.epochNumber

    // Check that the top quality certificate data is relevant to the SC active chain cert data.
    // There is no need to check endEpochBlockHash, epoch number and Snark proof, because SC trusts MC consensus.
    // Currently we need to check only the consistency of backward transfers and utxoMerkleRoot
    val expectedWithdrawalRequests = stateView.getWithdrawalRequests(certReferencedEpochNumber)

    // Simple size check
    if (topQualityCertificate.backwardTransferOutputs.size != expectedWithdrawalRequests.size) {
      throw new IllegalStateException(
        s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
          s"number ${topQualityCertificate.backwardTransferOutputs.size} is different than expected ${expectedWithdrawalRequests.size}. " +
          s"Node's active chain is the fork from MC perspective."
      )
    }

    // Check that BTs are identical for both Cert and State
    topQualityCertificate.backwardTransferOutputs.zip(expectedWithdrawalRequests).foreach {
      case (certOutput, expectedWithdrawalRequest) =>
        if (
          certOutput.amount != expectedWithdrawalRequest.valueInZennies ||
            !util.Arrays.equals(certOutput.pubKeyHash, expectedWithdrawalRequest.proposition.bytes())
        ) {
          throw new IllegalStateException(
            s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
              s"data is different than expected. Node's active chain is the fork from MC perspective."
          )
        }
    }
    //sc2sc validation
    if (sc2scConfig.canSendMessages){
      validateTopQualityCertificateForSc2Sc(topQualityCertificate, certReferencedEpochNumber, params.sidechainCreationVersion)
    }
  }

  // Note: Equal to SidechainState.isSwitchingConsensusEpoch
  override def isSwitchingConsensusEpoch(blockTimeStamp: Long): Boolean = {
    val blockConsensusEpoch: ConsensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, blockTimeStamp)
    val currentConsensusEpoch: ConsensusEpochNumber = getConsensusEpochNumber.getOrElse(intToConsensusEpochNumber(0))

    blockConsensusEpoch != currentConsensusEpoch
  }

  override def rollbackTo(version: VersionTag): Try[AccountState] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    val newMetaState = stateMetadataStorage.rollback(new ByteArrayWrapper(versionToBytes(version))).get
    new AccountState(params, sc2scConfig, timeProvider, blockHashProvider, version, newMetaState, stateDbStorage, messageProcessors)
  } recoverWith { case exception =>
    log.error("Exception was thrown during rollback.", exception)
    Failure(exception)
  }

  // versions part
  override def maxRollbackDepth: Int = stateMetadataStorage.rollbackVersions.size

  // View
  override def getView: AccountStateView = {
    // get state root
    val stateRoot = new Hash(stateMetadataStorage.getAccountStateRoot)
    val statedb = new StateDB(stateDbStorage, stateRoot)

    new AccountStateView(stateMetadataStorage.getView, statedb, messageProcessors)
  }

  // get a view over state db which is built with the given state root
  def getStateDbViewFromRoot(stateRoot: Array[Byte]): StateDbAccountStateView =
    new StateDbAccountStateView(new StateDB(stateDbStorage, new Hash(stateRoot)), messageProcessors)

  // Base getters
  override def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest] =
    using(getView)(_.getWithdrawalRequests(withdrawalEpoch))

  override def backwardTransfers(withdrawalEpoch: Int): Seq[BackwardTransfer] =
    using(getView)(_.getWithdrawalRequests(withdrawalEpoch))
      .map(wr => new BackwardTransfer(wr.proposition.bytes(), wr.valueInZennies))

  override def getCrossChainMessages(withdrawalEpoch: Int): Seq[CrossChainMessage] = {
    if (sc2scConfig.canSendMessages) using(getView)(_.getCrossChainMessages(withdrawalEpoch)) else Seq()
  }

  override def getCrossChainMessageHashEpoch(messageHash: CrossChainMessageHash): Option[Int] =
    if (sc2scConfig.canSendMessages) using(getView)(_.getCrossChainMessageHashEpoch(messageHash)) else None

  def getTopCertificateMainchainHash(withdrawalEpoch: Int): Option[Array[Byte]] =
    using(getView)(_.getTopCertificateMainchainHash(withdrawalEpoch))


  override def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof] = {
    using(getView)(_.keyRotationProof(withdrawalEpoch, indexOfSigner, keyType))
  }

  override def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys] = {
    if (withdrawalEpoch == -1 || params.circuitType == NaiveThresholdSignatureCircuit)
      Some(CertifiersKeys(params.signersPublicKeys.toVector, params.mastersPublicKeys.toVector))
    else {
      using(getView)(_.certifiersKeys(withdrawalEpoch))
    }
  }

  override def lastCertificateReferencedEpoch: Option[Int] = stateMetadataStorage.lastCertificateReferencedEpoch

  override def lastCertificateSidechainBlockId(): Option[ModifierId] =
    stateMetadataStorage.lastCertificateSidechainBlockId

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    getTopQualityCertificate(referencedWithdrawalEpoch)

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    stateMetadataStorage.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def hasCeased: Boolean = stateMetadataStorage.hasCeased

  override def getFeePaymentsInfo(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment] = {
    val feePaymentInfoSeq = stateMetadataStorage.getFeePayments(withdrawalEpoch)
    AccountFeePaymentsUtils.getForgersRewards(feePaymentInfoSeq)
  }

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = stateMetadataStorage.getWithdrawalEpochInfo

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = stateMetadataStorage.getConsensusEpochNumber

  override def getOrderedForgingStakesInfoSeq: Seq[ForgingStakeInfo] = using(getView)(_.getOrderedForgingStakesInfoSeq)

  // Returns lastBlockInEpoch and ConsensusEpochInfo for that epoch
  // TODO this is common code with SidechainState
  override def getCurrentConsensusEpochInfo: (ModifierId, ConsensusEpochInfo) = {
    val forgingStakes: Seq[ForgingStakeInfo] = getOrderedForgingStakesInfoSeq
    if (forgingStakes.isEmpty) {
      throw new IllegalStateException("ForgerStakes list can't be empty.")
    }

    getConsensusEpochNumber match {
      case Some(consensusEpochNumber) =>
        val lastBlockInEpoch = bytesToId(stateMetadataStorage.lastVersionId.get.data) // we use block id as version
        val consensusEpochInfo = ConsensusEpochInfo(
          consensusEpochNumber,
          MerkleTree.createMerkleTree(forgingStakes.map(info => info.hash).asJava),
          forgingStakes.map(_.stakeAmount).sum
        )
        (lastBlockInEpoch, consensusEpochInfo)
      case _ =>
        throw new IllegalStateException("Can't retrieve Consensus Epoch related info form StateStorage.")
    }
  }

  // Account specific getters
  override def getBalance(address: Address): BigInteger = using(getView)(_.getBalance(address))

  override def getAccountStateRoot: Array[Byte] = stateMetadataStorage.getAccountStateRoot

  override def getCodeHash(address: Address): Array[Byte] = using(getView)(_.getCodeHash(address))

  override def getNonce(address: Address): BigInteger = using(getView)(_.getNonce(address))

  override def getListOfForgersStakes: Seq[AccountForgingStakeInfo] = using(getView)(_.getListOfForgersStakes)

  override def getAllowedForgerList: Seq[Int] = using(getView)(_.getAllowedForgerList)

  override def getForgerStakeData(stakeId: String): Option[ForgerStakeData] = using(getView)(_.getForgerStakeData(stakeId))

  override def getLogs(txHash: Array[Byte]): Array[EvmLog] = using(getView)(_.getLogs(txHash))

  override def getIntermediateRoot: Array[Byte] = using(getView)(_.getIntermediateRoot)

  override def getCode(address: Address): Array[Byte] = using(getView)(_.getCode(address))

  override def getNextBaseFee: BigInteger = using(getView)(_.getNextBaseFee)

  override def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] = using(getView)(_.getTransactionReceipt(txHash))

  override def getStateDbHandle: ResourceHandle = using(getView)(_.getStateDbHandle)

  override def getAccountStorage(address: Address, key: Array[Byte]): Array[Byte] = using(getView)(_.getAccountStorage(address, key))

  override def getAccountStorageBytes(address: Address, key: Array[Byte]): Array[Byte] = using(getView)(_.getAccountStorageBytes(address, key))

  override def accountExists(address: Address): Boolean = using(getView)(_.accountExists(address))

  override def isEoaAccount(address: Address): Boolean = using(getView)(_.isEoaAccount(address))

  override def isSmartContractAccount(address: Address): Boolean = using(getView)(_.isSmartContractAccount(address))

  override def validate(tx: SidechainTypes#SCAT): Try[Unit] = Try {

    if (!tx.isInstanceOf[EthereumTransaction]) {
      val errMsg = s"Transaction ${tx.id}: instance of class ${tx.getClass.getName}, not of type ${classOf[EthereumTransaction].getName}"
      log.error(errMsg)
      throw new IllegalArgumentException(errMsg)
    }
    val ethTx = tx.asInstanceOf[EthereumTransaction]

    if (ethTx.isEIP155 || ethTx.isEIP1559) {
      if (ethTx.getChainId != params.chainId) {
        val errMsg = s"Transaction ${ethTx.id}: chainId=${ethTx.getChainId} is different from expected SC chainId ${params.chainId}"
        log.error(errMsg)
        throw new InvalidTransactionChainIdException(errMsg)
      }
    }

    ethTx.semanticValidity()

    if (FeeUtils.GAS_LIMIT.compareTo(ethTx.getGasLimit) < 0)
      throw new IllegalArgumentException(s"Transaction gas limit exceeds block gas limit: tx gas limit ${ethTx.getGasLimit}, block gas limit ${FeeUtils.GAS_LIMIT}")

    using(getView) { stateView =>
        //Check the nonce
        val sender = ethTx.getFrom.address()
        val stateNonce = stateView.getNonce(sender)
        if (stateNonce.compareTo(ethTx.getNonce) > 0) {
          throw NonceTooLowException(sender, ethTx.getNonce, stateNonce)
        }
        //Check the balance

        val maxTxCost = ethTx.maxCost()

        val currentBalance = stateView.getBalance(sender)
        if (currentBalance.compareTo(maxTxCost) < 0) {
          throw new IllegalArgumentException(s"Insufficient funds for executing transaction: balance $currentBalance, tx cost ${ethTx.maxCost}")
        }

        // Check that the sender is an EOA
        if (!stateView.isEoaAccount(sender))
          throw SenderNotEoaException(sender, stateView.getCodeHash(sender))

      }
  } recoverWith { case t =>
    log.debug(s"Not valid transaction ${tx.id}", t)
    Failure(t)
  }

  // Check that State is on the last index of the withdrawal epoch: last block applied have finished the epoch.
  override def isWithdrawalEpochLastIndex: Boolean = {
    WithdrawalEpochUtils.isEpochLastIndex(getWithdrawalEpochInfo, params)
  }

  override def isForgingOpen: Boolean = {
    if (params.restrictForgers)
      using(getView)(_.isForgingOpen)
    else
      true
  }

  override def utxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]] = {
    // TODO: no CSW support expected for the Eth sidechain
    None
  }

  override def doesCrossChainMessageHashFromRedeemMessageExist(hash: CrossChainMessageHash): Boolean =
    if (sc2scConfig.canSendMessages) using(getView)(_.doesCrossChainMessageHashFromRedeemMessageExist(hash)) else false

}

object AccountState extends SparkzLogging {
  private[horizen] def restoreState(
      stateMetadataStorage: AccountStateMetadataStorage,
      stateDbStorage: Database,
      messageProcessors: Seq[MessageProcessor],
      params: NetworkParams,
      sc2ScConfigurator: Sc2ScConfigurator,
      timeProvider: NetworkTimeProvider,
      blockHashProvider: HistoryBlockHashProvider
  ): Option[AccountState] = {

    if (stateMetadataStorage.isEmpty) {
      None
    } else {
      Some(
        new AccountState(
          params,
          sc2ScConfigurator,
          timeProvider,
          blockHashProvider,
          bytesToVersion(stateMetadataStorage.lastVersionId.get.data),
          stateMetadataStorage,
          stateDbStorage,
          messageProcessors
        )
      )
    }
  }

  private[horizen] def createGenesisState(
      stateMetadataStorage: AccountStateMetadataStorage,
      stateDbStorage: Database,
      messageProcessors: Seq[MessageProcessor],
      params: NetworkParams,
      sc2ScConfigurator: Sc2ScConfigurator,
      timeProvider: NetworkTimeProvider,
      blockHashProvider: HistoryBlockHashProvider,
      genesisBlock: AccountBlock
  ): Try[AccountState] = Try {

    if (!stateMetadataStorage.isEmpty) throw new RuntimeException("State metadata storage is not empty!")

    new AccountState(
      params,
      sc2ScConfigurator,
      timeProvider,
      blockHashProvider,
      idToVersion(genesisBlock.parentId),
      stateMetadataStorage,
      stateDbStorage,
      messageProcessors
    )
      .initProcessors(idToVersion(genesisBlock.parentId))
      .get
      .applyModifier(genesisBlock)
      .get
  }
}
