package com.horizen.account.state

import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.evm.ResourceHandle
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.Address

import java.math.BigInteger

trait AccountStateReader {
  def getStateDbHandle: ResourceHandle
  def getAccountStorage(address: Address, key: Array[Byte]): Array[Byte]
  def getAccountStorageBytes(address: Address, key: Array[Byte]): Array[Byte]

  def accountExists(address: Address): Boolean
  def isEoaAccount(address: Address): Boolean
  def isSmartContractAccount(address: Address): Boolean

  def getNonce(address: Address): BigInteger
  def getBalance(address: Address): BigInteger
  def getCodeHash(address: Address): Array[Byte]
  def getCode(address: Address): Array[Byte]

  def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest]

  def getListOfForgersStakes: Seq[AccountForgingStakeInfo]
  def getForgerStakeData(stakeId: String): Option[ForgerStakeData]
  def isForgingOpen: Boolean
  def getAllowedForgerList: Seq[Int]

  def getLogs(txHash: Array[Byte]): Array[EvmLog]
  def getIntermediateRoot: Array[Byte]

  def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys]
  def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof]
}
