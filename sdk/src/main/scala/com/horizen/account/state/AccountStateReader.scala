package com.horizen.account.state

import com.horizen.account.transaction.EthereumTransaction
import com.horizen.evm.interop.EvmLog
import com.horizen.state.BaseStateReader

import java.math.BigInteger

trait AccountStateReader extends BaseStateReader {

  def getNonce(address: Array[Byte]): BigInteger
  def getBalance(address: Array[Byte]): BigInteger
  def getCodeHash(address: Array[Byte]): Array[Byte]
  def getCode(address: Array[Byte]): Array[Byte]
  def getAccountStateRoot: Array[Byte] // 32 bytes, keccak hash

  def getListOfForgerStakes: Seq[AccountForgingStakeInfo]
  def getForgerStakeData(stakeId: String): Option[ForgerStakeData]

  def getLogs(txHash: Array[Byte]): Array[EvmLog]
  def getIntermediateRoot: Array[Byte]

  def getHeight: Int

  def getBaseFeePerGas: BigInteger
  def getBlockGasLimit: BigInteger
  def getTxFeesPerGas(tx: EthereumTransaction) : (BigInteger, BigInteger)

}
