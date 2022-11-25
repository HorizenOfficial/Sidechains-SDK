package com.horizen.account.state

import com.horizen.evm.interop.EvmLog
import java.math.BigInteger

trait AccountStateReader {
  def getNonce(address: Array[Byte]): BigInteger
  def getBalance(address: Array[Byte]): BigInteger
  def getCodeHash(address: Array[Byte]): Array[Byte]
  def getCode(address: Array[Byte]): Array[Byte]
  def getAccountStateRoot: Array[Byte] // 32 bytes, keccak hash

  def getListOfForgerStakes: Seq[AccountForgingStakeInfo]
  def getForgerStakeData(stakeId: String): Option[ForgerStakeData]

  def getLogs(txHash: Array[Byte]): Array[EvmLog]
  def getIntermediateRoot: Array[Byte]
  //Contains the base fee to be used when forging the next block
  def nextBaseFee: BigInteger

}
