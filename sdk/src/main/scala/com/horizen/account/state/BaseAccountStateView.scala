package com.horizen.account.state

import com.horizen.evm.ResourceHandle
import com.horizen.evm.interop.EvmLog

import java.math.BigInteger
import scala.util.Try

trait BaseAccountStateView extends AccountStateReader {
  def getStateDbHandle: ResourceHandle

  def isEoaAccount(address: Array[Byte]): Boolean

  def addBalance(address: Array[Byte], amount: BigInteger): Try[Unit]

  def subBalance(address: Array[Byte], amount: BigInteger): Try[Unit]

  def increaseNonce(address: Array[Byte]): Try[Unit]

  def isSmartContractAccount(address: Array[Byte]): Boolean

  def getAccountStorage(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]]

  def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]]

  def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit]

  def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit]

  def accountExists(address: Array[Byte]): Boolean

  def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Try[Unit]

  def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Try[Unit]

  def addAccount(address: Array[Byte], codeHash: Array[Byte]): Try[Unit]

  def addLog(evmLog: EvmLog): Try[Unit]
}
