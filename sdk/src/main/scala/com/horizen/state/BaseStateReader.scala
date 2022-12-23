package com.horizen.state

import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment}
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.WithdrawalEpochInfo
import scorex.util.ModifierId

import java.math.BigInteger

trait BaseStateReader {
  def getWithdrawalEpochInfo: WithdrawalEpochInfo
  def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]
  def getFeePaymentsInfo(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment]
  def getConsensusEpochNumber: Option[ConsensusEpochNumber]
  def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt]
  def getNextBaseFee: BigInteger //Contains the base fee to be used when forging the next block
  def hasCeased: Boolean
  def getAccountStateRoot: Array[Byte] // 32 bytes, keccak hash
  //def lastCertificateReferencedEpoch: Option[Int]
  //def lastCertificateSidechainBlockId(): Option[ModifierId]
  //def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof]
  //def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys]
  //def utxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]]
}
