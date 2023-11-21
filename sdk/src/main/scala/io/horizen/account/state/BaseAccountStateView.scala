package io.horizen.account.state

import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.evm.Address

import java.math.BigInteger

trait BaseAccountStateView extends AccountStateReader {

  def addAccount(address: Address, code: Array[Byte]): Unit
  def increaseNonce(address: Address): Unit

  @throws(classOf[ExecutionFailedException])
  def addBalance(address: Address, amount: BigInteger): Unit
  @throws(classOf[ExecutionFailedException])
  def subBalance(address: Address, amount: BigInteger): Unit

  def updateAccountStorage(address: Address, key: Array[Byte], value: Array[Byte]): Unit
  def updateAccountStorageBytes(address: Address, key: Array[Byte], value: Array[Byte]): Unit
  def removeAccountStorage(address: Address, key: Array[Byte]): Unit
  def removeAccountStorageBytes(address: Address, key: Array[Byte]): Unit

  def addLog(log: EthereumConsensusDataLog): Unit

  def getGasTrackedView(gas: GasPool): BaseAccountStateView

  def getNativeSmartContractAddressList(): Array[Address]
}
