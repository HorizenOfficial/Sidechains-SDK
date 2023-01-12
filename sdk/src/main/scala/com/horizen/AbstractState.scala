package com.horizen

import java.util

import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import com.horizen.block.{MainchainBlockReferenceData, SidechainBlockBase, SidechainBlockHeaderBase, WithdrawalEpochCertificate}
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.certnative.BackwardTransfer
import com.horizen.consensus.ConsensusEpochInfo
import com.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider, CustomFieldsReservedPositions}
import com.horizen.librustsidechains.FieldElement
import com.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash}
import com.horizen.transaction.Transaction
import com.horizen.utils.WithdrawalEpochInfo
import scorex.util.ModifierId
import sparkz.core.transaction.state.MinimalState

import scala.collection.JavaConverters._

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
  def getTopCertificateMainchainHash(withdrawalEpoch: Int): Option[Array[Byte]];

  protected def validateTopQualityCertificateForSc2Sc(topQualityCertificate: WithdrawalEpochCertificate,
                                                      certReferencedEpochNumber: Int,
                                                      sidechainCreationVersion: SidechainCreationVersion): Unit = {

    val certificateCustomFields = topQualityCertificate.fieldElementCertificateFields
    if (certificateCustomFields.size != CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION) {
      throw new IllegalArgumentException(s"Top quality certificate should contain exactly ${CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION} custom fields when sidechain2sidechain messaging is enabled.")
    }
    val messageTreeRoot = certificateCustomFields(CustomFieldsReservedPositions.Sc2sc_message_tree_root.position()).rawData
    val previousCertificateHash = certificateCustomFields(CustomFieldsReservedPositions.Sc2sc_previus_certificate_hash.position()).rawData

    val expectedPreviousCertificateHash: Array[Byte] = certificate(topQualityCertificate.epochNumber - 1) match {
      case None => FieldElement.createFromLong(0).serializeFieldElement()
      case Some(value) => CryptoLibProvider.commonCircuitFunctions.getCertDataHash(value, sidechainCreationVersion)
    }
    if (!util.Arrays.equals(previousCertificateHash, expectedPreviousCertificateHash)) {
      throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate has incorrect previousCertificateHash field")
    }
    val expectedCrosschainMessages = getCrossChainMessages(certReferencedEpochNumber)
    val expectedMessageTreeroot = CryptoLibProvider.sc2scCircuitFunctions.getCrossChainMessageTreeRoot(expectedCrosschainMessages.toList.asJava)
    if (!util.Arrays.equals(messageTreeRoot, expectedMessageTreeroot)) {
      throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate has incorrect crosschain message tree root field")
    }
  }

}




