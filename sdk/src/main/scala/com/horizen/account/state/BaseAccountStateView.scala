package com.horizen.account.state

import com.horizen.evm.ResourceHandle
import com.horizen.evm.interop.EvmLog

import java.math.BigInteger

trait BaseAccountStateView extends AccountStateReader {
  def getStateDbHandle: ResourceHandle

  def accountExists(address: Array[Byte]): Boolean
  def isEoaAccount(address: Array[Byte]): Boolean
  def isSmartContractAccount(address: Array[Byte]): Boolean

  def addAccount(address: Array[Byte], codeHash: Array[Byte]): Unit
  def increaseNonce(address: Array[Byte]): Unit

  @throws(classOf[ExecutionFailedException])
  def addBalance(address: Array[Byte], amount: BigInteger): Unit
  @throws(classOf[ExecutionFailedException])
  def subBalance(address: Array[Byte], amount: BigInteger): Unit

  @throws(classOf[OutOfGasException])
  def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte]
  @throws(classOf[OutOfGasException])
  def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte]
  @throws(classOf[OutOfGasException])
  def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit
  @throws(classOf[OutOfGasException])
  def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit
  @throws(classOf[OutOfGasException])
  def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Unit
  @throws(classOf[OutOfGasException])
  def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Unit

  @throws(classOf[OutOfGasException])
  def addLog(evmLog: EvmLog): Unit

  def enableGasTracking(gasPool: GasPool): Unit
  def disableGasTracking(): Unit
}
