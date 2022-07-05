package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.node.NodeAccountState
import com.horizen.account.storage.AccountStateMetadataStorage
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.{ConsensusEpochInfo, ConsensusEpochNumber, ForgingStakeInfo, intToConsensusEpochNumber}
import com.horizen.evm._
import com.horizen.params.NetworkParams
import com.horizen.state.State
import com.horizen.utils.{BlockFeeInfo, ByteArrayWrapper, BytesUtils, FeePaymentsUtils, MerkleTree, TimeToEpochUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import scorex.core._
import scorex.util.{ModifierId, ScorexLogging}

import java.math.BigInteger
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Try}

class AccountState(val params: NetworkParams,
                   override val version: VersionTag,
                   stateMetadataStorage: AccountStateMetadataStorage,
                   stateDbStorage: Database,
                   messageProcessors: Seq[MessageProcessor])
  extends State[SidechainTypes#SCAT, AccountBlock, AccountStateView, AccountState]
    with NodeAccountState
    with ScorexLogging {

  override type NVCT = AccountState

  // Execute MessageProcessors initialization phase
  // Used once on genesis AccountState creation
  private def initProcessors(initialVersion: VersionTag): Try[AccountState] = Try {
    val view = getView
    for (processor <- messageProcessors) {
      processor.init(view)
    }
    // TODO: commit only StateDB part ???
    // If we commit only stateDb we will not be able to
    // start a new view from the last rootHash
    view.commit(initialVersion)
    view.close()
    this
  }

  // Modifiers:
  override def applyModifier(mod: AccountBlock): Try[AccountState] = Try {
    require(versionToBytes(version).sameElements(idToBytes(mod.parentId)),
      s"Incorrect state version!: ${mod.parentId} found, " + s"$version expected")

    var stateView: AccountStateView = getView

    if (stateView.hasCeased) {
      throw new IllegalStateException(s"Can't apply Block ${mod.id}, because the sidechain has ceased.")
    }

    // Check Txs semantic validity first
    for (tx <- mod.sidechainTransactions)
      tx.semanticValidity()

    // Validate top quality certificate in the end of the submission window:
    // Reject block if it refers to the chain that conflicts with the top quality certificate content
    // Mark sidechain as ceased in case there is no certificate appeared within the submission window.
    val currentWithdrawalEpochInfo = stateView.getWithdrawalEpochInfo
    val modWithdrawalEpochInfo: WithdrawalEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(mod, currentWithdrawalEpochInfo, params)

    // If SC block has reached the certificate submission window end -> check the top quality certificate
    // Note: even if mod contains multiple McBlockRefData entries, we are sure they belongs to the same withdrawal epoch.
    if (WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(mod, currentWithdrawalEpochInfo, params)) {
      val certReferencedEpochNumber = modWithdrawalEpochInfo.epoch - 1

      // Top quality certificate may present in the current SC block or in the previous blocks or can be absent.
      val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = mod.topQualityCertificateOpt.orElse(
        stateView.certificate(certReferencedEpochNumber))

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

    // Update view with the block info
    stateView.updateWithdrawalEpochInfo(modWithdrawalEpochInfo).get

    val consensusEpochNum: ConsensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, mod.timestamp)
    stateView.updateConsensusEpochNumber(consensusEpochNum)

    // If SC block has reached the end of the withdrawal epoch -> fee payments expected to be produced.
    // Verify that Forger assumed the same fees to be paid as the current node does.
    // If SC block is in the middle of the withdrawal epoch -> no fee payments hash expected to be defined.
    val isWithdrawalEpochFinished: Boolean = WithdrawalEpochUtils.isEpochLastIndex(modWithdrawalEpochInfo, params)
    if (isWithdrawalEpochFinished) {
      // Note: that current block fee info is already in the view
      // TODO: get the list of block info and recalculate the root of it
      val feePayments = stateView.getBlockFeePayments(modWithdrawalEpochInfo.epoch)
      val feePaymentsHash: Array[Byte] = new Array[Byte](32) // TODO: analog of FeePaymentsUtils.calculateFeePaymentsHash(feePayments)

      if (!mod.feePaymentsHash.sameElements(feePaymentsHash))
        throw new IllegalArgumentException(s"Block ${mod.id} has feePaymentsHash different to expected one: ${BytesUtils.toHexString(feePaymentsHash)}")
    } else {
      // No fee payments expected
      if (!mod.feePaymentsHash.sameElements(FeePaymentsUtils.DEFAULT_FEE_PAYMENTS_HASH))
        throw new IllegalArgumentException(s"Block ${mod.id} has feePaymentsHash ${BytesUtils.toHexString(mod.feePaymentsHash)} defined when no fee payments expected.")
    }

    for(mcBlockRefData <- mod.mainchainBlockReferencesData) {
      stateView.applyMainchainBlockReferenceData(mcBlockRefData).get
    }

    // TODO get also list of receipts consensus data, useful for computing the receiptRoot hash
    for (tx <- mod.sidechainTransactions) {
      stateView = stateView.applyTransaction(tx).get
    }

    // TODO: calculate and update fee info.
    // Note: we should save the total gas paid and the forgerAddress
    stateView.addFeeInfo(BlockFeeInfo(0L, mod.header.forgingStakeInfo.blockSignPublicKey)).get

    // TODO get block hash and update receipts derived data (we used only consensus data for getting the receiptsRoot)
    // check stateRoot and receiptRoot against block header
    // eventually, store full receipts in the metaDataStorage indexed by txid

    stateView.commit(idToVersion(mod.id)).get

    new AccountState(params, idToVersion(mod.id), stateMetadataStorage, stateDbStorage, messageProcessors)
  }

  private def validateTopQualityCertificate(topQualityCertificate: WithdrawalEpochCertificate, stateView: AccountStateView): Unit = {
    val certReferencedEpochNumber: Int = topQualityCertificate.epochNumber

    // Check that the top quality certificate data is relevant to the SC active chain cert data.
    // There is no need to check endEpochBlockHash, epoch number and Snark proof, because SC trusts MC consensus.
    // Currently we need to check only the consistency of backward transfers and utxoMerkleRoot
    val expectedWithdrawalRequests = stateView.withdrawalRequests(certReferencedEpochNumber)

    // Simple size check
    if (topQualityCertificate.backwardTransferOutputs.size != expectedWithdrawalRequests.size) {
      throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
        s"number ${topQualityCertificate.backwardTransferOutputs.size} is different than expected ${expectedWithdrawalRequests.size}. " +
        s"Node's active chain is the fork from MC perspective.")
    }

    // Check that BTs are identical for both Cert and State
    topQualityCertificate.backwardTransferOutputs.zip(expectedWithdrawalRequests).foreach {
      case (certOutput, expectedWithdrawalRequest) => {
        if (certOutput.amount != expectedWithdrawalRequest.valueInZennies ||
          !util.Arrays.equals(certOutput.pubKeyHash, expectedWithdrawalRequest.proposition.bytes())) {
          throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
            s"data is different than expected. Node's active chain is the fork from MC perspective.")
        }
      }
    }

    // TODO: no CSW support expected for the Eth sidechain
    /*if(topQualityCertificate.fieldElementCertificateFields.size != 2)
      throw new IllegalArgumentException(s"Top quality certificate should contain exactly 2 custom fields.")

    utxoMerkleTreeRoot(certReferencedEpochNumber) match {
      case Some(expectedMerkleTreeRoot) =>
        val certUtxoMerkleRoot = CryptoLibProvider.sigProofThresholdCircuitFunctions.reconstructUtxoMerkleTreeRoot(
          topQualityCertificate.fieldElementCertificateFields.head.fieldElementBytes(params.sidechainCreationVersion),
          topQualityCertificate.fieldElementCertificateFields(1).fieldElementBytes(params.sidechainCreationVersion)
        )
        if(!expectedMerkleTreeRoot.sameElements(certUtxoMerkleRoot))
          throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate utxo merkle tree root " +
            s"data is different than expected. Node's active chain is the fork from MC perspective.")
      case None =>
        throw new IllegalArgumentException(s"There is no utxo merkle tree root stored for the referenced epoch $certReferencedEpochNumber.")
    }*/
  }

  // Note: Equal to SidechainState.isSwitchingConsensusEpoch
  def isSwitchingConsensusEpoch(mod: AccountBlock): Boolean = {
    val blockConsensusEpoch: ConsensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, mod.timestamp)
    val currentConsensusEpoch: ConsensusEpochNumber = getConsensusEpochNumber.getOrElse(intToConsensusEpochNumber(0))

    blockConsensusEpoch != currentConsensusEpoch
  }

  override def rollbackTo(version: VersionTag): Try[AccountState] = {
    Try {
      require(version != null, "Version to rollback to must be NOT NULL.")
      val newMetaState = stateMetadataStorage.rollback(new ByteArrayWrapper(versionToBytes(version))).get

      new AccountState(params, version,
        newMetaState,
        stateDbStorage,
        messageProcessors)
    }.recoverWith({
      case exception =>
        log.error("Exception was thrown during rollback.", exception)
        Failure(exception)
    })
  }

  // versions part
  override def maxRollbackDepth: Int = stateMetadataStorage.rollbackVersions.size

  // View
  override def getView: AccountStateView = {
    // get state root
    val stateRoot = stateMetadataStorage.getAccountStateRoot.getOrElse(new Array[Byte](32))
    val statedb = new StateDB(stateDbStorage, stateRoot)

    new AccountStateView(stateMetadataStorage.getView, statedb, messageProcessors)
  }

  def getStateDbViewFromRoot(stateRoot: Array[Byte]) : AccountStateView =
    new AccountStateView(stateMetadataStorage.getView, new StateDB(stateDbStorage, stateRoot), messageProcessors)

  // getters:
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest] = {
    log.error("TODO - needs to be implemented")
    Seq()
  }

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    val stateView: AccountStateView = getView
    val res = stateView.certificate(referencedWithdrawalEpoch)
    stateView.close()
    res
  }

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = {
    val stateView: AccountStateView = getView
    val res = stateView.certificateTopQuality(referencedWithdrawalEpoch)
    stateView.close()
    res
  }

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    val stateView: AccountStateView = getView
    val res = stateView.getWithdrawalEpochInfo
    stateView.close()
    res
  }

  override def hasCeased: Boolean = {
    val stateView: AccountStateView = getView
    val res = stateView.hasCeased
    stateView.close()
    res
  }

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = {
    val stateView: AccountStateView = getView
    val res = stateView.getConsensusEpochNumber
    stateView.close()
    res
  }

  def getOrderedForgingStakesInfoSeq : Seq[ForgingStakeInfo] = {
    val stateView: AccountStateView = getView
    val res = stateView.getOrderedForgingStakeInfoSeq
    stateView.close()
    res
  }

  // Returns lastBlockInEpoch and ConsensusEpochInfo for that epoch
  // Identical to the SidechainState.getCurrentConsensusEpochInfo method
  // TODO this is common code with SidechainState
  def getConsensusEpochInfo: (ModifierId, ConsensusEpochInfo) = {
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
          forgingStakes.map(_.stakeAmount).sum)
        (lastBlockInEpoch, consensusEpochInfo)
      case _ =>
        throw new IllegalStateException("Can't retrieve Consensus Epoch related info form StateStorage.")
    }
  }

  override def getBlockFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = {
    val view = getView
    val res = view.getBlockFeePayments(withdrawalEpochNumber)
    view.close()
    res
  }

  // Account specific getters
  override def getAccount(address: Array[Byte]): Account = getView.getAccount(address)

  override def getBalance(address: Array[Byte]): Try[java.math.BigInteger] = {
    val view = getView
    val res = view.getBalance(address)
    view.close()
    res
  }

  override def getAccountStateRoot: Option[Array[Byte]] = {
    val view = getView
    val res = view.getAccountStateRoot
    view.close()
    res
  }

  override def getCodeHash(address: Array[Byte]): Array[Byte] = {
    val view = getView
    val res = view.getCodeHash(address)
    view.close()
    res
  }

  override def getNonce(address: Array[Byte]): BigInteger = {
    val view = getView
    val res = view.getNonce(address)
    view.close()
    res
  }
}


object AccountState {
  private[horizen] def restoreState(stateMetadataStorage: AccountStateMetadataStorage,
                                    stateDbStorage: Database,
                                    messageProcessors: Seq[MessageProcessor],
                                    params: NetworkParams): Option[AccountState] = {

    if (!stateMetadataStorage.isEmpty) {
      Some(new AccountState(params, bytesToVersion(stateMetadataStorage.lastVersionId.get.data), stateMetadataStorage,
        stateDbStorage, messageProcessors))
    } else
      None
  }

  private[horizen] def createGenesisState(stateMetadataStorage: AccountStateMetadataStorage,
                                          stateDbStorage: Database,
                                          messageProcessors: Seq[MessageProcessor],
                                          params: NetworkParams,
                                          genesisBlock: AccountBlock): Try[AccountState] = Try {

    if (stateMetadataStorage.isEmpty) {
      new AccountState(params, idToVersion(genesisBlock.parentId), stateMetadataStorage, stateDbStorage, messageProcessors)
        .initProcessors(idToVersion(genesisBlock.parentId)).get
        .applyModifier(genesisBlock).get
    } else
      throw new RuntimeException("State metadata storage is not empty!")
  }
}
