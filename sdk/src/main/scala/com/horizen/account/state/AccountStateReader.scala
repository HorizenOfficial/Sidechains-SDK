package com.horizen.account.state

import com.horizen.evm.ResourceHandle
import com.horizen.evm.interop.EvmLog

import java.math.BigInteger

trait AccountStateReader {
  def getStateDbHandle: ResourceHandle
  def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte]
  def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte]

  def accountExists(address: Array[Byte]): Boolean
  def isEoaAccount(address: Array[Byte]): Boolean
  def isSmartContractAccount(address: Array[Byte]): Boolean

  def getNonce(address: Array[Byte]): BigInteger
  def getBalance(address: Array[Byte]): BigInteger
  def getCodeHash(address: Array[Byte]): Array[Byte]
  def getCode(address: Array[Byte]): Array[Byte]

  def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest]

  def getListOfForgerStakes: Seq[AccountForgingStakeInfo]
  def getForgerStakeData(stakeId: String): Option[ForgerStakeData]

  def getLogs(txHash: Array[Byte]): Array[EvmLog]
  def getIntermediateRoot: Array[Byte]

}
