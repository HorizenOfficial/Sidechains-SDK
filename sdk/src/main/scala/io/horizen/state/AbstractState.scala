package io.horizen

import io.horizen.block.{MainchainHeaderHash, SidechainBlockBase, SidechainBlockHeaderBase, WithdrawalEpochCertificate}
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.certnative.BackwardTransfer
import com.horizen.librustsidechains.FieldElement
import io.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import io.horizen.consensus.ConsensusEpochInfo
import io.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider, CustomFieldsReservedPositions}
import io.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash}
import io.horizen.transaction.Transaction
import io.horizen.utils.WithdrawalEpochInfo
import sparkz.util.ModifierId
import sparkz.core.transaction.state.MinimalState

import java.util
import scala.collection.JavaConverters._
import scala.util.Using

abstract class AbstractState[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  MS <: AbstractState[TX, H, PM, MS]
] extends MinimalState[PM, MS] {
  self: MS =>

  // abstract methods
  def isSwitchingConsensusEpoch(blockTimestamp: Long): Boolean

  def isWithdrawalEpochLastIndex: Boolean

  def getWithdrawalEpochInfo: WithdrawalEpochInfo

  def getCurrentConsensusEpochInfo: (ModifierId, ConsensusEpochInfo)

  //Check if the majority of the allowed forgers opened the stake to everyone
  def isForgingOpen: Boolean

  def lastCertificateReferencedEpoch: Option[Int]
  def lastCertificateSidechainBlockId(): Option[ModifierId]
  def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof]
  def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys]
  def backwardTransfers(withdrawalEpoch: Int): Seq[BackwardTransfer]

  def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]
  def utxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]]

  //methods needed for Sidechain2Sidechian:
  def getCrossChainMessages(withdrawalEpoch: Int): Seq[CrossChainMessage]

  def getCrossChainMessageHashEpoch(messageHash: CrossChainMessageHash): Option[Int]

  //hash of mainchain block that published the top quality cert of this epoch
  def getTopCertificateMainchainHash(withdrawalEpoch: Int): Option[MainchainHeaderHash]

  protected def validateTopQualityCertificateForSc2Sc(topQualityCertificate: WithdrawalEpochCertificate,
                                                      certReferencedEpochNumber: Int,
                                                      sidechainCreationVersion: SidechainCreationVersion): Unit = {

    val certificateCustomFields = topQualityCertificate.fieldElementCertificateFields
    if (certificateCustomFields.size != CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION) {
      throw new IllegalArgumentException(s"Top quality certificate should contain exactly ${CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION} custom fields when sidechain2sidechain messaging is enabled.")
    }
    val messageTreeRoot = certificateCustomFields(CustomFieldsReservedPositions.SC2SC_MESSAGE_TREE_ROOT.position()).rawData
    val previousCertificateHash = certificateCustomFields(CustomFieldsReservedPositions.SC2SC_PREVIOUS_CERTIFICATE_HASH.position()).rawData

    val expectedPreviousCertificateHash: Array[Byte] = certificate(topQualityCertificate.epochNumber - 1) match {
      case None => FieldElement.createFromLong(0).serializeFieldElement()
      case Some(value) => CryptoLibProvider.commonCircuitFunctions.getCertDataHash(value, sidechainCreationVersion)
    }
    if (!util.Arrays.equals(previousCertificateHash, expectedPreviousCertificateHash)) {
      throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate has incorrect previousCertificateHash field")
    }
    val expectedCrosschainMessages = getCrossChainMessages(certReferencedEpochNumber)

    Using.resource(
      CryptoLibProvider.sc2scCircuitFunctions.initMerkleTree()
    ) { tree => {
      CryptoLibProvider.sc2scCircuitFunctions.appendMessagesToMerkleTree(tree, expectedCrosschainMessages.asJava)
      val expectedMessageTreeRoot = CryptoLibProvider.sc2scCircuitFunctions.getCrossChainMessageTreeRoot(tree)

      if (!util.Arrays.equals(messageTreeRoot, expectedMessageTreeRoot)) {
        throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate has incorrect crosschain message tree root field")
      }
    }}
  }
}