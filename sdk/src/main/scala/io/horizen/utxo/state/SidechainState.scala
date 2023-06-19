package io.horizen.utxo.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.certnative.BackwardTransfer
import io.horizen.block.{MainchainHeaderHash, SidechainBlockBase, WithdrawalEpochCertificate}
import io.horizen.certificatesubmitter.keys.KeyRotationProofTypes.{KeyRotationProofType, MasterKeyRotationProofType, SigningKeyRotationProofType}
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import io.horizen.consensus._
import io.horizen.cryptolibprovider.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.cryptolibprovider.{CircuitTypes, CommonCircuit, CryptoLibProvider}
import io.horizen.fork.{ForkManager, Sc2ScFork}
import io.horizen.params.{NetworkParams, NetworkParamsUtils}
import io.horizen.proposition.{Proposition, PublicKey25519Proposition, SchnorrProposition, VrfPublicKey}
import io.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash}
import io.horizen.transaction.MC2SCAggregatedTransaction
import io.horizen.transaction.exception.TransactionSemanticValidityException
import io.horizen.utils.{ByteArrayWrapper, BytesUtils, MerkleTree, TimeToEpochUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import io.horizen.utxo.backup.BoxIterator
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.box._
import io.horizen.utxo.box.data.ZenBoxData
import io.horizen.utxo.crosschain.CrossChainValidator
import io.horizen.utxo.crosschain.receiver.CrossChainRedeemMessageValidator
import io.horizen.utxo.crosschain.validation.sender.CrossChainMessageValidator
import io.horizen.utxo.forge.ForgerList
import io.horizen.utxo.node.NodeState
import io.horizen.utxo.storage.{BackupStorage, SidechainStateForgerBoxStorage, SidechainStateStorage}
import io.horizen.utxo.transaction.{CertificateKeyRotationTransaction, OpenStakeTransaction, SidechainTransaction}
import io.horizen.utxo.utils.{BlockFeeInfo, FeePaymentsUtils}
import io.horizen.{AbstractState, SidechainTypes}
import sparkz.core._
import sparkz.core.transaction.state._
import sparkz.core.utils.TimeProvider
import sparkz.crypto.hash.Blake2b256
import sparkz.util.{ModifierId, SparkzLogging, bytesToId}

import java.io.File
import java.math.{BigDecimal, MathContext}
import java.util
import java.util.{ArrayList => JArrayList, HashMap => JHashMap, Optional => JOptional}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class SidechainState private[horizen](stateStorage: SidechainStateStorage,
                                      forgerBoxStorage: SidechainStateForgerBoxStorage,
                                      utxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider,
                                      val params: NetworkParams,
                                      override val version: VersionTag,
                                      val applicationState: ApplicationState,
                                      timeProvider: TimeProvider)
  extends AbstractState[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainState]
    with TransactionValidation[SidechainTypes#SCBT]
    with ModifierValidation[SidechainBlock]
    with SidechainTypes
    with NodeState
    with SparkzLogging
    with UtxoMerkleTreeView
    with NetworkParamsUtils {
  override type NVCT = SidechainState

  private lazy val crossChainValidators: Seq[CrossChainValidator[SidechainBlock]] = Seq(
    new CrossChainMessageValidator(this, params, timeProvider),
    new CrossChainRedeemMessageValidator(stateStorage, CryptoLibProvider.sc2scCircuitFunctions, params)
  )

  lazy val verificationKeyFullFilePath: String = {
    if (params.certVerificationKeyFilePath.equalsIgnoreCase("")) {
      throw new IllegalStateException(s"Verification key file name is not set")
    }

    val verificationFile: File = new File(params.certProvingKeyFilePath)
    if (!verificationFile.canRead) {
      throw new IllegalStateException(s"Verification key file at path ${verificationFile.getAbsolutePath} does not exist or can't be read")
    }
    else {
      log.info(s"Verification key file at location: ${verificationFile.getAbsolutePath}")
      verificationFile.getAbsolutePath
    }
  }

  // Note: emit tx.semanticValidity for each tx
  def semanticValidity(tx: SidechainTypes#SCBT): Try[Unit] = Try {
    tx.semanticValidity()
  }

  // get closed box from storages
  def closedBox(boxId: Array[Byte]): Option[SidechainTypes#SCB] = {
    stateStorage
      .getBox(boxId)
      .orElse(forgerBoxStorage.getForgerBox(boxId).asInstanceOf[Option[SidechainTypes#SCB]])
  }

  override def getClosedBox(boxId: Array[Byte]): JOptional[Box[_ <: Proposition]] = {
    closedBox(boxId) match {
      case Some(box) => JOptional.of(box)
      case None => JOptional.empty()
    }
  }

  def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = {
    stateStorage.getWithdrawalRequests(withdrawalEpoch)
  }

  def backwardTransfers(withdrawalEpoch: Int): Seq[BackwardTransfer] = {
    stateStorage.getWithdrawalRequests(withdrawalEpoch)
      .map(box => new BackwardTransfer(box.proposition.bytes, box.value))
  }

  override def getCrossChainMessages(withdrawalEpoch: Int): Seq[CrossChainMessage] = {
    val sc2ScFork = Sc2ScFork.get(TimeToEpochUtils.timeStampToEpochNumber(params, timeProvider.time()))

    if (sc2ScFork.sc2ScCanSend) stateStorage.getCrossChainMessagesPerEpoch(withdrawalEpoch) else Seq()
  }

  override def getCrossChainMessageHashEpoch(messageHash: CrossChainMessageHash): Option[Int] = {
    val sc2ScFork = Sc2ScFork.get(TimeToEpochUtils.timeStampToEpochNumber(params, timeProvider.time()))

    if (sc2ScFork.sc2ScCanSend) stateStorage.getCrossChainMessageHashEpoch(messageHash) else None
  }

  override def getTopCertificateMainchainHash(withdrawalEpoch: Int): Option[MainchainHeaderHash] = {
    stateStorage.getTopQualityCertificateMainchainHeaderHash(withdrawalEpoch)
  }


  override def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof] = {
    stateStorage.getKeyRotationProof(withdrawalEpoch, indexOfSigner, keyType)
  }

  /**
   * Searches for the certifiers keys data actual at the end of the given withdrawal epoch
   *
   * @param withdrawalEpoch
   * withdrawal epoch number, at the end of which the certifiers keys where defined/stored
   * @return certifier keys in case the given withdrawal epoch has been finished and the record is still in the database,
   *         None otherwise.
   * @note in case {@code withdrawalEpoch == -1}, then returns the genesis set of certifiers keys from params.
   */
  override def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys] = {
    if (withdrawalEpoch == -1)
      Option.apply(CertifiersKeys(params.signersPublicKeys.toVector, params.mastersPublicKeys.toVector))
    else {
      stateStorage.getCertifiersKeys(withdrawalEpoch)
    }
  }

  override def utxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]] = {
    stateStorage.getUtxoMerkleTreeRoot(withdrawalEpoch)
  }

  override def utxoMerklePath(boxId: Array[Byte]): Option[Array[Byte]] = {
    utxoMerkleTreeProvider.getMerklePath(boxId)
  }

  override def hasCeased: Boolean = stateStorage.hasCeased

  def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    stateStorage.getTopQualityCertificate(referencedWithdrawalEpoch)
  }

  def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = {
    certificate(referencedWithdrawalEpoch) match {
      case Some(cert) => cert.quality
      case None => 0 // there are no certificates for epoch
    }
  }

  override def lastCertificateReferencedEpoch(): Option[Int] = {
    stateStorage.getLastCertificateReferencedEpoch()
  }

  /*
   * Returns the id of Sidechain block with the last top quality certificate certificate.
   * Note: has sense only for non-ceasing sidechains. Always returns `None` for ceasing sidechains.
   */
  override def lastCertificateSidechainBlockId(): Option[ModifierId] = {
    stateStorage.getLastCertificateSidechainBlockId()
  }

  def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    stateStorage.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0, 0))
  }

  // Note: aggregate New boxes and spent boxes for Block
  def changes(mod: SidechainBlock): Try[BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB]] = {
    SidechainState.changes(mod)
  }

  // Validate block itself: version and semanticValidity for block
  override def validate(mod: SidechainBlock): Try[Unit] = Try {
    require(versionToBytes(version).sameElements(idToBytes(mod.parentId)),
      s"Incorrect state version!: ${mod.parentId} found, " + s"$version expected")

    if (hasCeased) {
      throw new IllegalStateException(s"Can't apply Block ${mod.id}, because the sidechain has ceased.")
    }
    val consensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, mod.timestamp)

    val currentWithdrawalEpochInfo = stateStorage.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0, 0))
    val modWithdrawalEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(mod.mainchainBlockReferencesData.size, currentWithdrawalEpochInfo, params)

    validateBlockTransactionsMutuality(mod)
    mod.transactions.foreach(tx => {
      validate(tx, consensusEpochNumber, modWithdrawalEpochInfo.epoch).get
    })
    crossChainValidators.foreach(validator => validator.validate(mod))

    if (params.isNonCeasing) {
      // For non-ceasing sidechains certificate must be validated just when it has been received.
      // In case of multiple certificates appeared and at least one of them is invalid (conflicts with the current chain)
      // then the whole block is invalid.
      mod.mainchainBlockReferencesData.flatMap(_.topQualityCertificate).foreach(cert => validateTopQualityCertificate(cert, cert.epochNumber))
    } else {
      // For ceasing sidechains submission window concept is used.
      // If SC block has reached the certificate submission window end -> check the top quality certificate
      // Note: even if mod contains multiple McBlockRefData entries, we are sure they belongs to the same withdrawal epoch.
      if (WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(mod, currentWithdrawalEpochInfo, params)) {
        val certReferencedEpochNumber = modWithdrawalEpochInfo.epoch - 1

        // Top quality certificate may present in the current SC block or in the previous blocks or can be absent.
        val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = mod.topQualityCertificateOpt.orElse(
          stateStorage.getTopQualityCertificate(certReferencedEpochNumber))

        // Check top quality certificate or notify that sidechain has ceased since we have no certificate in the end of the submission window.
        topQualityCertificateOpt match {
          case Some(cert) =>
            validateTopQualityCertificate(cert, certReferencedEpochNumber)
          case None =>
            log.info(s"In the end of the certificate submission window of epoch ${modWithdrawalEpochInfo.epoch} " +
              s"there are no certificates referenced to the epoch $certReferencedEpochNumber. Sidechain has ceased.")
        }
      }
    }

    // If SC block has reached the end of the withdrawal epoch -> fee payments expected to be produced.
    // Verify that Forger assumed the same fees to be paid as the current node does.
    // If SC block is in the middle of the withdrawal epoch -> no fee payments hash expected to be defined.
    val isWithdrawalEpochFinished: Boolean = WithdrawalEpochUtils.isEpochLastIndex(modWithdrawalEpochInfo, params)
    if (isWithdrawalEpochFinished) {
      // Note: that current block fee info is still not in the state storage, so consider it during result calculation.
      val feePayments = getFeePayments(modWithdrawalEpochInfo.epoch, Some(mod.feeInfo))
      val feePaymentsHash: Array[Byte] = FeePaymentsUtils.calculateFeePaymentsHash(feePayments)

      if (!mod.feePaymentsHash.sameElements(feePaymentsHash))
        throw new IllegalArgumentException(s"Block ${mod.id} has feePaymentsHash different to expected one: ${BytesUtils.toHexString(feePaymentsHash)}")
    } else {
      // No fee payments expected
      if (!mod.feePaymentsHash.sameElements(FeePaymentsUtils.DEFAULT_FEE_PAYMENTS_HASH))
        throw new IllegalArgumentException(s"Block ${mod.id} has feePaymentsHash ${BytesUtils.toHexString(mod.feePaymentsHash)} defined when no fee payments expected.")
    }

    applicationState.validate(this, mod)
  }

  private def validateBlockTransactionsMutuality(mod: SidechainBlock): Unit = {
    val transactionsIds: Seq[String] = mod.transactions.map(_.id())
    if (transactionsIds.toSet.size != transactionsIds.size) {
      throw new IllegalArgumentException(s"Block ${mod.id} contains duplicated transactions")
    }

    val allInputBoxesIds: Seq[ByteArrayWrapper] = mod.transactions.flatMap(tx => tx.boxIdsToOpen().asScala)
    if (allInputBoxesIds.size != allInputBoxesIds.toSet.size) {
      throw new IllegalArgumentException(s"Block ${mod.id} contains duplicated input boxes to open")
    }

    val consensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, mod.timestamp)

    //Check that we don't have multiple openStake transactions with the same forgerIndex
    if (openStakeTransactionEnabled(Some(consensusEpochNumber))) {
      val forgerListIndexes = new JArrayList[Int]()
      mod.transactions.foreach(tx => {
        if (tx.isInstanceOf[OpenStakeTransaction]) {
          val openStakeTransaction = tx.asInstanceOf[OpenStakeTransaction]
          if (forgerListIndexes.contains(openStakeTransaction.getForgerIndex))
            throw new IllegalArgumentException(s"Block ${mod.id} contains OpenStakeTransactions with duplicated forgerIndex")
          forgerListIndexes.add(openStakeTransaction.getForgerIndex)
        }
      })
    }

    if (params.circuitType == NaiveThresholdSignatureCircuitWithKeyRotation) {
      val keyTypeMap = new JHashMap[KeyRotationProofType, Seq[Int]]()
      mod.transactions.foreach(tx => {
        if (tx.isInstanceOf[CertificateKeyRotationTransaction]) {
          val keyRotationTransaction = tx.asInstanceOf[CertificateKeyRotationTransaction]
          val keyType = keyRotationTransaction.getKeyRotationProof.keyType
          val keyIndex = keyRotationTransaction.getKeyRotationProof.index
          if (keyTypeMap.containsKey(keyType)) {
            if (keyTypeMap.get(keyType).contains(keyIndex))
              throw new IllegalArgumentException(s"Block ${mod.id} contains multiple KeyRotationTransactions pointing to the same key")
            else {
              val currentValue: Seq[Int] = keyTypeMap.get(keyType)
              keyTypeMap.put(keyType, currentValue :+ keyIndex)
            }
          } else {
            keyTypeMap.put(keyType, Seq(keyIndex))
          }
        }
      })
    }

    if (ForkManager.getSidechainFork(consensusEpochNumber).backwardTransferLimitEnabled)
      checkWithdrawalBoxesAllowed(mod)
  }

  private def checkWithdrawalBoxesAllowed(mod: SidechainBlock): Unit = {
    val alreadyMinedWBs = getAlreadyMinedWithdrawalRequestBoxesInCurrentEpoch
    val mainchainBlockReferenceInBlock = mod.mainchainBlockReferencesData.size
    val allowedWBs = getAllowedWithdrawalRequestBoxes(mainchainBlockReferenceInBlock)
    var blockWBs = alreadyMinedWBs
    mod.transactions.foreach(tx => {
      blockWBs += tx.newBoxes().asScala.count(box => box.isInstanceOf[WithdrawalRequestBox])
      if (blockWBs > allowedWBs) {
        throw new IllegalStateException(s"Exceeded the maximum number of WithdrawalBoxes allowed!")
      }
    })
  }

  def getAlreadyMinedWithdrawalRequestBoxesInCurrentEpoch: Int = {
    stateStorage.getWithdrawalRequests(getWithdrawalEpochInfo.epoch).size
  }

  def getAllowedWithdrawalRequestBoxes(numberOfMainchainBlockReferenceInBlock: Int): Int = {
    Math.min(params.maxWBsAllowed,
      (params.maxWBsAllowed * (getWithdrawalEpochInfo.lastEpochIndex + numberOfMainchainBlockReferenceInBlock)) / (params.withdrawalEpochLength - 1))
  }

  def getAlreadyMinedCrossChainMessagesInCurrentEpoch: Int = {
    stateStorage.getCrossChainMessagesPerEpoch(getWithdrawalEpochInfo.epoch).size
  }

  def getAllowedCrossChainMessageBoxes(numberOfMainchainBlockReferenceInBlock: Int, maxMessagesPerCertificate: Int): Int = {
    Math.min(maxMessagesPerCertificate,
      (maxMessagesPerCertificate * (getWithdrawalEpochInfo.lastEpochIndex + numberOfMainchainBlockReferenceInBlock)) / (params.withdrawalEpochLength - 1))
  }

  def openStakeTransactionEnabled(consensusEpochNumber: Option[ConsensusEpochNumber]): Boolean = {
    consensusEpochNumber match {
      case Some(consensusEpochNumber) =>
        ForkManager.getSidechainFork(consensusEpochNumber).openStakeTransactionEnabled
      case None =>
        false
    }
  }

  private def validateTopQualityCertificate(topQualityCertificate: WithdrawalEpochCertificate, certReferencedEpochNumber: Int): Unit = {
    val certReferencedEpochNumber: Int = topQualityCertificate.epochNumber

    // Check that the top quality certificate data is relevant to the SC active chain cert data.
    // There is no need to check endEpochBlockHash, epoch number and Snark proof, because SC trusts MC consensus.
    // Currently we need to check only the consistency of backward transfers and utxoMerkleRoot
    val expectedWithdrawalRequests = withdrawalRequests(certReferencedEpochNumber)

    // Simple size check
    if (topQualityCertificate.backwardTransferOutputs.size != expectedWithdrawalRequests.size) {
      throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
        s"number ${topQualityCertificate.backwardTransferOutputs.size} is different than expected ${expectedWithdrawalRequests.size}. " +
        s"Node's active chain is the fork from MC perspective.")
    }

    // Check that BTs are identical for both Cert and State
    topQualityCertificate.backwardTransferOutputs.zip(expectedWithdrawalRequests).foreach {
      case (certOutput, expectedWithdrawalRequestBox) =>
        if (certOutput.amount != expectedWithdrawalRequestBox.value() ||
          !util.Arrays.equals(certOutput.pubKeyHash, expectedWithdrawalRequestBox.proposition().bytes())) {
          throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
            s"data is different than expected. Node's active chain is the fork from MC perspective.")
        }
    }

    //sc2sc validation
    val sc2ScFork = Sc2ScFork.get(TimeToEpochUtils.timeStampToEpochNumber(params, timeProvider.time()))

    if (sc2ScFork.sc2ScCanSend) {
      validateTopQualityCertificateForSc2Sc(topQualityCertificate, certReferencedEpochNumber, params.sidechainCreationVersion)
    }

    if (params.circuitType == CircuitTypes.NaiveThresholdSignatureCircuitWithKeyRotation) {
      if (topQualityCertificate.fieldElementCertificateFields.size != CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION) {
        throw new IllegalArgumentException(s"Top quality certificate should contain exactly ${CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION} custom fields when ceased sidechain withdrawal is disabled and key rotation enabled.")
        // todo: verify the first field against the key rotation root hash. Others are zeros of FE size
      }
    } else {
      if (params.isCSWEnabled) {
        if (topQualityCertificate.fieldElementCertificateFields.size != CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW)
          throw new IllegalArgumentException(s"Top quality certificate should contain exactly ${CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW} custom fields.")

        utxoMerkleTreeRoot(certReferencedEpochNumber) match {
          case Some(expectedMerkleTreeRoot) =>
            val certUtxoMerkleRoot = CryptoLibProvider.sigProofThresholdCircuitFunctions.reconstructUtxoMerkleTreeRoot(
              topQualityCertificate.fieldElementCertificateFields.head.fieldElementBytes(params.sidechainCreationVersion),
              topQualityCertificate.fieldElementCertificateFields(1).fieldElementBytes(params.sidechainCreationVersion)
            )
            if (!expectedMerkleTreeRoot.sameElements(certUtxoMerkleRoot))
              throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate utxo merkle tree root " +
                s"data is different than expected. Node's active chain is the fork from MC perspective.")
          case None =>
            throw new IllegalArgumentException(s"There is no utxo merkle tree root stored for the referenced epoch $certReferencedEpochNumber.")
        }
      } else {
        if (topQualityCertificate.fieldElementCertificateFields.size != CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_NO_KEY_ROTATION)
          throw new IllegalArgumentException(s"Top quality certificate should contain exactly ${CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_NO_KEY_ROTATION} custom fields when ceased sidechain withdrawal is disabled.")
      }
    }
  }

  def validateWithFork(tx: SidechainTypes#SCBT, consensusEpochNumber: ConsensusEpochNumber): Try[Unit] = Try {
    val newBoxes = tx.newBoxes().asScala
    val newCoinBoxes = newBoxes
      .filter(box => box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]] || box.isInstanceOf[WithdrawalRequestBox])

    val coinBoxMinAmount = ForkManager.getSidechainFork(consensusEpochNumber).coinBoxMinAmount
    newCoinBoxes.foreach { coinBox =>
      if (coinBox.value() < coinBoxMinAmount)
        throw new TransactionSemanticValidityException(s"Transaction [${tx.id()}] is semantically invalid: " +
          s"Coin box value [${coinBox.value()}] is below the threshold[$coinBoxMinAmount].")
    }
  }

  def validateWithWithdrawalEpoch(tx: SidechainTypes#SCBT, withdrawalEpoch: Int): Try[Unit] = Try {
    if (tx.isInstanceOf[CertificateKeyRotationTransaction]) {
      if (params.circuitType == CircuitTypes.NaiveThresholdSignatureCircuit) {
        throw new Exception("CertificateKeyRotationTransaction is not allowed with this kind of circuit!")
      }
      val keyRotationTransaction: CertificateKeyRotationTransaction = tx.asInstanceOf[CertificateKeyRotationTransaction]
      val keyRotationProof = keyRotationTransaction.getKeyRotationProof
      val oldCertifiersKeys = certifiersKeys(withdrawalEpoch - 1).get

      val messageToSign = keyRotationProof.keyType match {
        case SigningKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
          .getMsgToSignForSigningKeyUpdate(keyRotationProof.newKey.pubKeyBytes(), withdrawalEpoch, params.sidechainId)
        case MasterKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
          .getMsgToSignForMasterKeyUpdate(keyRotationProof.newKey.pubKeyBytes(), withdrawalEpoch, params.sidechainId)
      }

      //Verify that the key index is in a valid range
      if (keyRotationProof.index < 0 || keyRotationProof.index > oldCertifiersKeys.masterKeys.size)
        throw new Exception("Key index in CertificateKeyRotationTransaction is out of range!")

      //Verify the signature using the old signing key
      if (!keyRotationProof.signingKeySignature.isValid(oldCertifiersKeys.signingKeys(keyRotationProof.index), messageToSign))
        throw new Exception("Signing key signature in CertificateKeyRotationTransaction is not valid!")

      //Verify the signature using the old master key
      if (!keyRotationProof.masterKeySignature.isValid(oldCertifiersKeys.masterKeys(keyRotationProof.index), messageToSign))
        throw new Exception("Master key signature in CertificateKeyRotationTransaction is not valid!")

      //Verify the signature using the new key
      if (!keyRotationTransaction.getNewKeySignature.isValid(keyRotationProof.newKey, messageToSign))
        throw new Exception("New key signature in CertificateKeyRotationTransaction is not valid!")
    }
  }

  override def validate(tx: SidechainTypes#SCBT): Try[Unit] = {
    stateStorage.getConsensusEpochNumber match {
      case Some(consensusEpochNumber) =>
        validate(tx, consensusEpochNumber, getWithdrawalEpochInfo.epoch)
      case None => throw new IllegalStateException("Can't retrieve Consensus Epoch related info form StateStorage.")
    }
  }

  // Note: Transactions validation in a context of inclusion in or exclusion from Mempool
  // Note 2: BT and FT is not included into memory pool and have another check rule.
  // TO DO: (almost the same as in NodeViewHolder)
  // 1) check if all unlocker are related to EXISTING CLOSED boxes (B) and able to open them
  // 2) check if for each B, that is instance of CoinBox interface, that total sum is equal to new CoinBox'es sum minus tx.fee
  // 3) if it's a Sidechain custom Transaction (not known) -> emit applicationState.validate(tx)
  // TO DO: put validateAgainstModifier logic inside validate(mod)
  private def validate(tx: SidechainTypes#SCBT, consensusEpochNumber: ConsensusEpochNumber, withdrawalEpoch: Int): Try[Unit] = Try {
    semanticValidity(tx).get

    var closedCoinsBoxesAmount: Long = 0L
    var newCoinsBoxesAmount: Long = 0L

    if (!tx.isInstanceOf[MC2SCAggregatedTransaction]) {

      if (tx.isInstanceOf[OpenStakeTransaction]) {
        if (!openStakeTransactionEnabled(Some(consensusEpochNumber)))
          throw new Exception("OpenStakeTransaction is still not allowed in this consensus epoch!")
        if (isForgingOpen())
          throw new Exception("OpenStakeTransactions are not allowed because the forger operation has already been opened!")
        val openStakeTransaction = tx.asInstanceOf[OpenStakeTransaction]
        if (openStakeTransaction.getForgerIndex >= params.allowedForgersList.size || openStakeTransaction.getForgerIndex < 0) {
          throw new Exception("ForgerIndex in OpenStakeTransaction is out of bound!")
        }
        stateStorage.getForgerList match {
          case Some(forgerList) =>
            if (openStakeTransaction.getForgerIndex >= forgerList.forgerIndexes.length) {
              throw new Exception("OpenStakeTransaction forgerIndex out of bound!")
            }
            if (forgerList.forgerIndexes(openStakeTransaction.getForgerIndex) == 1) {
              throw new Exception("Forger already opened the stake!")
            }
          case None =>
            throw new Exception("Forger list was not found in the Storage!")
        }
        stateStorage.getBox(openStakeTransaction.getInputId) match {
          case Some(closedBox) =>
            if (!closedBox.proposition().asInstanceOf[PublicKey25519Proposition]
              .equals(params.allowedForgersList(openStakeTransaction.getForgerIndex)._1)) {
              throw new Exception("OpenStakeTransaction input doesn't match the forgerIndex!")
            }
          case None =>
            throw new Exception("Input box not found!")
        }
      }

      for (u <- tx.unlockers().asScala) {
        closedBox(u.closedBoxId()) match {
          case Some(box) => {
            val boxKey = u.boxKey()
            if (!boxKey.isValid(box.proposition(), tx.messageToSign()))
              throw new Exception("Box unlocking proof is invalid.")
            if (box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]])
              closedCoinsBoxesAmount += box.value()
          }
          case None => throw new Exception(s"Box ${u.closedBoxId()} is not found in state")
        }
      }

      validateWithWithdrawalEpoch(tx, withdrawalEpoch).get
      validateWithFork(tx, consensusEpochNumber).get

      val newBoxes = tx.newBoxes().asScala

      newCoinsBoxesAmount = newBoxes
        .filter(box => box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]] || box.isInstanceOf[WithdrawalRequestBox])
        .map(_.value()).sum

      if (closedCoinsBoxesAmount != newCoinsBoxesAmount + tx.fee())
        throw new Exception("Amounts sum of CoinsBoxes is incorrect. " +
          s"ClosedBox amount - $closedCoinsBoxesAmount, NewBoxesAmount - $newCoinsBoxesAmount, Fee - ${tx.fee()}")

      lazy val isForgerOpen = isForgingOpen()
      newBoxes
        .filter(box => box.isInstanceOf[ForgerBox])
        .foreach(forgerBox => {
          if (!isForgerOpen) {
            val vrfPublicKey: VrfPublicKey = forgerBox.vrfPubKey()
            val blockSignProposition: PublicKey25519Proposition = forgerBox.blockSignProposition()
            if (!params.allowedForgersList.contains((blockSignProposition, vrfPublicKey))) {
              throw new Exception("This publicKey is not allowed to forge!")
            }
          }
        })
    }

    applicationState.validate(this, tx)
  }

  //Check if the majority of the allowed forgers opened the stake to everyone
  override def isForgingOpen(): Boolean = {
    if (!params.restrictForgers)
      true
    else {
      val nOpenForger: Int = stateStorage.getForgerList match {
        case Some(forgerList: ForgerList) =>
          forgerList.forgerIndexes.sum
        case None =>
          log.error("No forgerList found in the Storage!")
          0
      }
      nOpenForger > params.allowedForgersList.size / 2
    }
  }

  override def applyModifier(mod: SidechainBlock): Try[SidechainState] = {
    validate(mod).flatMap { _ =>
      changes(mod).flatMap(cs => {
        applyChanges(
          cs,
          idToVersion(mod.id),
          WithdrawalEpochUtils.getWithdrawalEpochInfo(mod.mainchainBlockReferencesData.size, stateStorage.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0, 0)), params),
          TimeToEpochUtils.timeStampToEpochNumber(params, mod.timestamp),
          SidechainBlockBase.getTopQualityCertsWithMainChainHash(mod.mainchainBlockReferencesData),
          mod.feeInfo,
          getRestrictForgerIndexToUpdate(mod.sidechainTransactions),
          getKeyRotationProofsToAdd(mod.sidechainTransactions),
          mod.mainchainHeaders.map(mcHeader => BytesUtils.toHexString(mcHeader.hashScTxsCommitment)).toSet
        )
      })
    }
  }

  //Take the list of transactions inside a block and calculate the forgerList indexes to update
  def getRestrictForgerIndexToUpdate(txs: Seq[SidechainTransaction[Proposition, Box[Proposition]]]): Array[Int] = {
    txs.flatMap(tx => {
      if (tx.isInstanceOf[OpenStakeTransaction]) {
        val openStakeTransaction: OpenStakeTransaction = tx.asInstanceOf[OpenStakeTransaction]
        Some(openStakeTransaction.getForgerIndex)
      } else {
        None
      }
    }).toArray
  }

  //Take the list of transactions inside a block and returns the key rotation proofs
  def getKeyRotationProofsToAdd(txs: Seq[SidechainTransaction[Proposition, Box[Proposition]]]): Seq[KeyRotationProof] = {
    params.circuitType match {
      case NaiveThresholdSignatureCircuit =>
        Seq[KeyRotationProof]()
      case NaiveThresholdSignatureCircuitWithKeyRotation =>
        txs.flatMap(tx => {
          if (tx.isInstanceOf[CertificateKeyRotationTransaction]) {
            val keyRotationTransaction: CertificateKeyRotationTransaction = tx.asInstanceOf[CertificateKeyRotationTransaction]
            Some(keyRotationTransaction.getKeyRotationProof)
          } else {
            None
          }
        })
    }
  }

  // apply global changes and delegate SDK unknown part to Sidechain.applyChanges(...)
  // 1) get boxes ids to remove, and boxes to append from "changes"
  // 2) call applicationState.applyChanges(changes):
  //    if ok -> return updated SDKState -> update SidechainState store
  //    if fail -> rollback applicationState
  // 3) ensure everything applied OK and return new SidechainState. If not -> return error
  def applyChanges(changes: BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB],
                   newVersion: VersionTag,
                   withdrawalEpochInfo: WithdrawalEpochInfo,
                   consensusEpoch: ConsensusEpochNumber,
                   topQualityCerts: Seq[(WithdrawalEpochCertificate, MainchainHeaderHash)],
                   blockFeeInfo: BlockFeeInfo,
                   forgerListIndexes: Array[Int],
                   keyRotationProofsToAdd: Seq[KeyRotationProof],
                   hashScTxsCommitment: Set[String]
                  ): Try[SidechainState] = Try {
    val version = new ByteArrayWrapper(versionToBytes(newVersion))
    var boxesToAppend = changes.toAppend.map(_.box)

    val withdrawalRequestsToAppend: ListBuffer[WithdrawalRequestBox] = ListBuffer()
    val crossChainMessagesToAppend: ListBuffer[CrossChainMessage] = ListBuffer()
    val crossChainMessageHashFromRedeemMessagesToAppend: ListBuffer[CrossChainMessageHash] = ListBuffer()
    val forgerBoxesToAppend: ListBuffer[ForgerBox] = ListBuffer()
    val otherBoxesToAppend: ListBuffer[SidechainTypes#SCB] = ListBuffer()

    val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = topQualityCerts.lastOption.map(_._1)

    // Check if current block application will lead to ceasing the sidechain
    val hasReachedCertificateSubmissionWindowEnd: Boolean = WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(
      withdrawalEpochInfo, stateStorage.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0, 0)), params)

    val scHasCeased: Boolean = !params.isNonCeasing && hasReachedCertificateSubmissionWindowEnd &&
      topQualityCertificateOpt.orElse(stateStorage.getTopQualityCertificate(withdrawalEpochInfo.epoch - 1)).isEmpty

    var actualCertifiersKeys: Option[CertifiersKeys] = Option.empty

    val isWithdrawalEpochFinished: Boolean = WithdrawalEpochUtils.isEpochLastIndex(withdrawalEpochInfo, params)
    if (isWithdrawalEpochFinished) {
      // Calculate and append fee payment boxes to the boxesToAppend
      // Note: that current block fee info is still not in the state storage, so consider it during result calculation.
      boxesToAppend ++= getFeePayments(withdrawalEpochInfo.epoch, Some(blockFeeInfo)).map(_.asInstanceOf[SidechainTypes#SCB])

      if (CircuitTypes.NaiveThresholdSignatureCircuitWithKeyRotation == params.circuitType) {
        val currentEpoch = withdrawalEpochInfo.epoch
        val certifierKeys = certifiersKeys(currentEpoch - 1).get
        val signerKeys = new JArrayList[SchnorrProposition]()
        val masterKeys = new JArrayList[SchnorrProposition]()
        for (i <- certifierKeys.signingKeys.indices) {
          keyRotationProof(currentEpoch, i, SigningKeyRotationProofType.id) match {
            case Some(keyRotationProof: KeyRotationProof) =>
              signerKeys.add(keyRotationProof.newKey)
            case None =>
              signerKeys.add(certifierKeys.signingKeys(i))
          }
          keyRotationProof(currentEpoch, i, MasterKeyRotationProofType.id) match {
            case Some(keyRotationProof: KeyRotationProof) =>
              masterKeys.add(keyRotationProof.newKey)
            case None =>
              masterKeys.add(certifierKeys.masterKeys(i))
          }
        }
        actualCertifiersKeys = Option.apply(CertifiersKeys(signerKeys.asScala.toVector, masterKeys.asScala.toVector))
      }

    }

    boxesToAppend.foreach(box => {
      if (box.isInstanceOf[ForgerBox])
        forgerBoxesToAppend.append(box)
      else if (box.isInstanceOf[WithdrawalRequestBox]) {
        withdrawalRequestsToAppend.append(box)
      } else if (box.isInstanceOf[CrossChainMessageBox]) {
        crossChainMessagesToAppend.append(SidechainState.buildCrosschainMessageFromUTXO(box.asInstanceOf[CrossChainMessageBox], params))
      } else if (box.isInstanceOf[CrossChainRedeemMessageBox]) {
        val ccMsg = box.asInstanceOf[CrossChainRedeemMessageBox].getCrossChainMessage
        val messageHash = ccMsg.getCrossChainMessageHash
        crossChainMessageHashFromRedeemMessagesToAppend.append(messageHash)
      } else {
        otherBoxesToAppend.append(box)
      }
    })

    applicationState.onApplyChanges(this,
      version.data,
      boxesToAppend.asJava,
      changes.toRemove.map(_.boxId.array).asJava) match {
      case Success(appState) =>
        val boxIdsToRemoveSet = changes.toRemove.map(r => new ByteArrayWrapper(r.boxId)).toSet

        val updatedUtxoMerkleTreeProvider = utxoMerkleTreeProvider.update(version, boxesToAppend, boxIdsToRemoveSet).get
        val utxoMerkleTreeRootOpt: Option[Array[Byte]] = if (isWithdrawalEpochFinished) {
          updatedUtxoMerkleTreeProvider.getMerkleTreeRoot
        } else {
          None
        }

        new SidechainState(
          stateStorage.update(version, withdrawalEpochInfo, otherBoxesToAppend.toSet, boxIdsToRemoveSet,
            withdrawalRequestsToAppend, crossChainMessagesToAppend, crossChainMessageHashFromRedeemMessagesToAppend, hashScTxsCommitment,
            consensusEpoch, topQualityCerts, blockFeeInfo, utxoMerkleTreeRootOpt,
            scHasCeased, forgerListIndexes, params.allowedForgersList.size, keyRotationProofsToAdd, actualCertifiersKeys).get,
          forgerBoxStorage.update(version, forgerBoxesToAppend, boxIdsToRemoveSet).get,
          updatedUtxoMerkleTreeProvider,
          params,
          newVersion,
          appState,
          timeProvider
        )
      case Failure(exception) => {
        log.error("call to onApplyChanges() method has failed: ", exception)
        throw exception
      }
    }
  }.recoverWith {
    case exception =>
      log.error("Exception was thrown during applyChanges.", exception)
      Failure(exception)
  }

  override def maxRollbackDepth: Int = {
    stateStorage.rollbackVersions.size
  }

  override def rollbackTo(to: VersionTag): Try[SidechainState] = Try {
    require(to != null, "Version to rollback to must be NOT NULL.")
    log.debug(s"rolling back state to version = ${to}")
    val version = BytesUtils.fromHexString(to)
    val bawVersion = new ByteArrayWrapper(version)

    val forgerBoxStorageNew = forgerBoxStorage.rollback(bawVersion).get
    val stateStorageNew = stateStorage.rollback(bawVersion).get
    val utxoMerkleTreeProviderNew = utxoMerkleTreeProvider.rollback(bawVersion).get

    applicationState.onRollback(version) match {
      case Success(appState) => {
        new SidechainState(
          stateStorageNew,
          forgerBoxStorageNew,
          utxoMerkleTreeProviderNew,
          params,
          to,
          appState,
          timeProvider)
      }
      case Failure(exception) => {
        log.error("call to applicationState.onRollback() method has failed: ", exception)
        throw exception
      }
    }
  }.recoverWith { case exception =>
    log.error("Exception was thrown during rollback.", exception)
    Failure(exception)
  }

  def isSwitchingConsensusEpoch(blockTimestamp: Long): Boolean = {
    val blockConsensusEpoch: ConsensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, blockTimestamp)
    val currentConsensusEpoch: ConsensusEpochNumber = stateStorage.getConsensusEpochNumber.getOrElse(intToConsensusEpochNumber(0))

    blockConsensusEpoch != currentConsensusEpoch
  }

  // Returns lastBlockInEpoch and ConsensusEpochInfo for that epoch
  def getCurrentConsensusEpochInfo: (ModifierId, ConsensusEpochInfo) = {
    val forgingStakes: Seq[ForgingStakeInfo] = getOrderedForgingStakesInfoSeq()
    if (forgingStakes.isEmpty) {
      throw new IllegalStateException("ForgerStakes list can't be empty.")
    }

    stateStorage.getConsensusEpochNumber match {
      case Some(consensusEpochNumber) =>
        val lastBlockInEpoch = bytesToId(stateStorage.lastVersionId.get.data) // we use block id as version
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

  // Note: we consider ordering of the result to keep it deterministic for all Nodes.
  // From biggest stake to lowest, in case of equal compare vrf and block sign keys as well.
  def getOrderedForgingStakesInfoSeq(): Seq[ForgingStakeInfo] = {
    ForgingStakeInfo.fromForgerBoxes(forgerBoxStorage.getAllForgerBoxes).sorted(Ordering[ForgingStakeInfo].reverse)
  }

  // Check that State is on the last index of the withdrawal epoch: last block applied have finished the epoch.
  def isWithdrawalEpochLastIndex: Boolean = {
    WithdrawalEpochUtils.isEpochLastIndex(stateStorage.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0, 0)), params)
  }

  // Check that all storages are consistent and in case try some rollbacks.
  // Return the state and common version, throw an exception if some unrecoverable misalignment has been detected
  def ensureStorageConsistencyAfterRestore: Try[(SidechainState)] = Try {
    // updates are in order:
    //      appState--> utxoMerkleTreeStorage --> stateStorage --> forgerBoxStorage

    // get the version of the last updated storage and check that the others have the same
    // version
    val versionFbBytes = forgerBoxStorage.lastVersionId.get.data()
    val appStateVersionOk = applicationState.checkStoragesVersion(versionFbBytes)

    val versionFb = bytesToId(versionFbBytes)

    if (appStateVersionOk) {
      // appState is aligned with the last storage version, require that also intermediate storages have the same version

      if (params.isCSWEnabled) {
        val versionUmt = bytesToId(utxoMerkleTreeProvider.lastVersionId.get.data())
        require(versionFb == versionUmt, "ForgerBox and Utxo storage versions must be aligned")
      }

      val versionSt = bytesToId(stateStorage.lastVersionId.get.data())
      require(versionFb == versionSt, "ForgerBox and State storage versions must be aligned")
      require(versionFb == versionToId(version), "ForgerBox version and SidechainState version attribute must be aligned")
      log.debug("All state storages are consistent")

      this
    } else {
      log.debug("state storages are not consistent")

      val rolledBackState = rollbackTo(idToVersion(versionFb))
      if (rolledBackState.isFailure) {
        throw new IllegalStateException("Could not rollback state")
      } else {
        rolledBackState.get
      }
    }
  }

  // Collect Fee payments while appending the last withdrawal epoch block (optional), considering that block fee info as well.
  def getFeePayments(withdrawalEpochNumber: Int, blockToAppendFeeInfo: Option[BlockFeeInfo] = None): Seq[ZenBox] = {
    var blockFeeInfoSeq: Seq[BlockFeeInfo] = stateStorage.getFeePayments(withdrawalEpochNumber)
    blockToAppendFeeInfo.foreach(blockFeeInfo => blockFeeInfoSeq = blockFeeInfoSeq :+ blockFeeInfo)

    if (blockFeeInfoSeq.isEmpty) {
      return Seq()
    }

    var poolFee: Long = 0
    val forgerPercentage: BigDecimal = new BigDecimal(params.forgerBlockFeeCoefficient, MathContext.DECIMAL64)

    val forgersBlockRewards: Seq[(PublicKey25519Proposition, Long)] = blockFeeInfoSeq.map(feeInfo => {
      val forgerBlockFee: Long = new BigDecimal(feeInfo.fee).multiply(forgerPercentage).longValue()
      poolFee += (feeInfo.fee - forgerBlockFee)
      (feeInfo.forgerRewardKey, forgerBlockFee)
    })

    // Split poolFee in equal parts to be paid to forgers.
    val forgerPoolFee: Long = poolFee / forgersBlockRewards.size
    // The rest N satoshis must be paid to the first N forgers (1 satoshi each)
    val rest = poolFee % forgersBlockRewards.size

    // Calculate final fee for forger considering forger fee, pool fee and the undistributed satoshis
    val forgersRewards = forgersBlockRewards.zipWithIndex.map {
      case (forgerBlockReward: (PublicKey25519Proposition, Long), index: Int) =>
        val finalForgerFee = forgerBlockReward._2 + forgerPoolFee + (if (index < rest) 1 else 0)
        (forgerBlockReward._1, finalForgerFee)
    }

    // Aggregate together payments for the same forgers
    val forgerKeys: Seq[PublicKey25519Proposition] = forgersRewards.map(_._1).distinct
    val res: Seq[(PublicKey25519Proposition, Long)] = forgerKeys.map { forgerKey =>
      val forgerTotalFee: Long = forgersRewards.withFilter(r => forgerKey.equals(r._1)).map(_._2).sum
      (forgerKey, forgerTotalFee)
    }

    // Create and return Boxes with payments
    // Remove boxes with zero values, that may occur, for example, if all the blocks were without fees.
    res.zipWithIndex.map {
      case (forgerRewardInfo: (PublicKey25519Proposition, Long), index: Int) =>
        val data = new ZenBoxData(forgerRewardInfo._1, forgerRewardInfo._2)
        // Note: must be replaced with the Poseidon hash later.
        val nonce = SidechainState.calculateFeePaymentBoxNonce(withdrawalEpochNumber, index)
        new ZenBox(data, nonce)
    }.filter(box => box.value() > 0)
  }

  def restoreBackup(backupStorageBoxIterator: BoxIterator, lastVersion: Array[Byte]): Try[SidechainState] = Try {
    stateStorage.restoreBackup(backupStorageBoxIterator, lastVersion)
    backupStorageBoxIterator.seekToFirst()
    applicationState.onBackupRestore(backupStorageBoxIterator) match {
      case Success(_) =>
        this
      case Failure(e) =>
        log.error("Error during the backup restore inside the SidechainState", e)
        throw e
    }
  }
}


object SidechainState {
  def changes(mod: SidechainBlock): Try[BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB]] = Try {
    val initial = (Seq(): Seq[Array[Byte]], Seq(): Seq[SidechainTypes#SCB], 0L)

    val (toRemove: Seq[Array[Byte]], toAdd: Seq[SidechainTypes#SCB], reward) =
      mod.transactions.foldLeft(initial) { case ((sr, sa, f), tx) =>
        (sr ++ tx.unlockers().asScala.map(_.closedBoxId()), sa ++ tx.newBoxes().asScala, f + tx.fee())
      }

    // calculate list of ID of unlockers' boxes -> toRemove
    // calculate list of new boxes -> toAppend
    // calculate the rewards for Miner/Forger -> create another regular tx OR Forger need to add his Reward during block creation
    @SuppressWarnings(Array("org.wartremover.warts.Product", "org.wartremover.warts.Serializable"))
    val ops: Seq[BoxStateChangeOperation[SidechainTypes#SCP, SidechainTypes#SCB]] =
    toRemove.map(id => Removal[SidechainTypes#SCP, SidechainTypes#SCB](sparkz.crypto.authds.ADKey(id))) ++
      toAdd.map(b => Insertion[SidechainTypes#SCP, SidechainTypes#SCB](b))

    BoxStateChanges[SidechainTypes#SCP, SidechainTypes#SCB](ops)

    // Q: Do we need to call some static method of ApplicationState?
    // A: Probably yes. To remove some out of date boxes, like VoretBallotRight box for previous voting epoch.
    // Note: we need to implement a lot of limitation for changes from ApplicationState (only deletion, only non coin related boxes, etc.)
  }

  private[horizen] def restoreState(stateStorage: SidechainStateStorage,
                                    forgerBoxStorage: SidechainStateForgerBoxStorage,
                                    utxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider,
                                    params: NetworkParams,
                                    applicationState: ApplicationState,
                                    timeProvider: TimeProvider): Option[SidechainState] = {

    if (!stateStorage.isEmpty) {
      Some(new SidechainState(stateStorage, forgerBoxStorage, utxoMerkleTreeProvider,
        params, bytesToVersion(stateStorage.lastVersionId.get.data), applicationState, timeProvider)
      )
    } else
      None
  }

  private[horizen] def createGenesisState(stateStorage: SidechainStateStorage,
                                          forgerBoxStorage: SidechainStateForgerBoxStorage,
                                          utxoMerkleTreeProvider: SidechainStateUtxoMerkleTreeProvider,
                                          backupStorage: BackupStorage,
                                          params: NetworkParams,
                                          applicationState: ApplicationState,
                                          genesisBlock: SidechainBlock,
                                          timeProvider: TimeProvider): Try[SidechainState] = Try {

    if (stateStorage.isEmpty) {
      var state = new SidechainState(
        stateStorage, forgerBoxStorage, utxoMerkleTreeProvider, params,
        idToVersion(genesisBlock.parentId), applicationState, timeProvider
      )
      if (!backupStorage.isEmpty) {
        state = state.restoreBackup(backupStorage.getBoxIterator, versionToBytes(idToVersion(genesisBlock.parentId))).get
      }
      state.applyModifier(genesisBlock).get
    } else
      throw new RuntimeException("State storage is not empty!")
  }

  private[horizen] def calculateFeePaymentBoxNonce(withdrawalEpochNumber: Int, index: Int): Long = {
    val hash = Blake2b256.hash(Bytes.concat(Ints.toByteArray(withdrawalEpochNumber), Ints.toByteArray(index)))
    BytesUtils.getLong(hash, 0)
  }

  private[horizen] def buildCrosschainMessageFromUTXO(box: CrossChainMessageBox, params: NetworkParams): CrossChainMessage = {
    new CrossChainMessage(
      box.getProtocolVersion,
      box.getMessageType,
      BytesUtils.toMainchainFormat(params.sidechainId),
      box.proposition().pubKeyBytes(),
      box.getReceiverSidechain,
      box.getReceiverAddress,
      box.getPayload
    )
  }
}