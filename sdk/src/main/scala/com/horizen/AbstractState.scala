package com.horizen

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase, WithdrawalEpochCertificate}
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.certnative.BackwardTransfer
import com.horizen.consensus.ConsensusEpochInfo
import com.horizen.transaction.Transaction
import com.horizen.utils.WithdrawalEpochInfo
import scorex.util.ModifierId
import sparkz.core.transaction.state.MinimalState

abstract class AbstractState[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  MS <: AbstractState[TX, H, PM, MS]
] extends MinimalState[PM, MS]
{
  self: MS =>

  // abstract methods
  def isSwitchingConsensusEpoch(blockTimestamp: Long): Boolean
  def isWithdrawalEpochLastIndex: Boolean
  def getWithdrawalEpochInfo: WithdrawalEpochInfo
  def getCurrentConsensusEpochInfo: (ModifierId, ConsensusEpochInfo)
  def lastCertificateReferencedEpoch: Option[Int]
  def lastCertificateSidechainBlockId(): Option[ModifierId]
  def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof]
  def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys]
  def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]
  def backwardTransfers(withdrawalEpoch: Int): Seq[BackwardTransfer]
  def utxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]]
}


