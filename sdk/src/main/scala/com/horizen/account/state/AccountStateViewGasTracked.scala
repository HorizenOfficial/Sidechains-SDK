package com.horizen.account.state

import com.horizen.evm.ResourceHandle
import com.horizen.evm.interop.EvmLog

import java.math.BigInteger

/**
 * Wrapper for AccountStateView to help with tracking gas consumption.
 * @param view
 *   instance of BaseAccountStateView
 * @param gas
 *   GasPool instance to deduct gas from
 */
class AccountStateViewGasTracked(view: AccountStateView, gas: GasPool) extends BaseAccountStateView {
  override def getStateDbHandle: ResourceHandle = view.getStateDbHandle

  override def addAccount(address: Array[Byte], code: Array[Byte]): Unit = view.addAccount(address, code)

  /**
   * Consume gas for account access:
   *   - charge ColdAccountAccessCostEIP2929 if account was not accessed yet
   *   - charge WarmStorageReadCostEIP2929 if account was already accessed
   */
  @throws(classOf[OutOfGasException])
  private def accountAccess(address: Array[Byte]): Unit = {
    val warm = view.stateDb.accessAccount(address)
    gas.subGas(if (warm) GasUtil.WarmStorageReadCostEIP2929 else GasUtil.ColdAccountAccessCostEIP2929)
  }

  /**
   * Consume for account storage access:
   *   - charge ColdSloadCostEIP2929 if account storage slot was not accessed yet
   *   - charge WarmStorageReadCostEIP2929 if account storage slot was already accessed
   */
  @throws(classOf[OutOfGasException])
  private def slotRead(address: Array[Byte], slot: Array[Byte]): Unit = {
    val warm = view.stateDb.accessSlot(address, slot)
    gas.subGas(if (warm) GasUtil.WarmStorageReadCostEIP2929 else GasUtil.ColdSloadCostEIP2929)
  }

  override def accountExists(address: Array[Byte]): Boolean = {
    accountAccess(address)
    view.accountExists(address)
  }

  @throws(classOf[OutOfGasException])
  override def isEoaAccount(address: Array[Byte]): Boolean = {
    accountAccess(address)
    view.isEoaAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def isSmartContractAccount(address: Array[Byte]): Boolean = {
    accountAccess(address)
    view.isSmartContractAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCodeHash(address: Array[Byte]): Array[Byte] = {
    accountAccess(address)
    view.getCodeHash(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCode(address: Array[Byte]): Array[Byte] = {
    accountAccess(address)
    val code = view.getCode(address)
    // cosume additional gas proportional to the code size,
    // this should preferably be done before acutally copying the code, but we don't know the size beforehand
    gas.subGas(GasUtil.codeCopy(code.length))
    code
  }

  override def getNonce(address: Array[Byte]): BigInteger = {
    accountAccess(address)
    view.getNonce(address)
  }

  override def increaseNonce(address: Array[Byte]): Unit = {
    accountAccess(address)
    view.increaseNonce(address)
  }

  @throws(classOf[OutOfGasException])
  override def getBalance(address: Array[Byte]): BigInteger = {
    accountAccess(address)
    view.getBalance(address)
  }

  @throws(classOf[OutOfGasException])
  override def addBalance(address: Array[Byte], amount: BigInteger): Unit = {
    accountAccess(address)
    view.addBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def subBalance(address: Array[Byte], amount: BigInteger): Unit = {
    accountAccess(address)
    view.subBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    slotRead(address, key)
    view.getAccountStorage(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    // here we read 1 word overhead (storing the length) plus N bytes of data spread over M words
    // so we charge warm or cold gas for the word storing the length, depending on previous access to the same key
    // and we charge M*warm gas
    slotRead(address, key)
    val value = view.getAccountStorageBytes(address, key)
    val words = (value.length + 31) / 32
    gas.subGas(GasUtil.WarmStorageReadCostEIP2929.multiply(BigInteger.valueOf(words)))
    value
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    // TODO: complicated SSTORE gas usage + refunds handling
    gas.subGas(GasUtil.GasTBD)
    view.updateAccountStorage(address, key, value)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    // TODO: complicated SSTORE gas usage + refunds handling
    gas.subGas(GasUtil.GasTBD)
    view.updateAccountStorageBytes(address, key, value)
  }

  @throws(classOf[OutOfGasException])
  override def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Unit = {
    // TODO: complicated SSTORE gas usage + refunds handling
    gas.subGas(GasUtil.GasTBD)
    view.removeAccountStorage(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Unit = {
    // TODO: complicated SSTORE gas usage + refunds handling
    gas.subGas(GasUtil.GasTBD)
    view.removeAccountStorageBytes(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def addLog(evmLog: EvmLog): Unit = {
    gas.subGas(GasUtil.logGas(evmLog))
    view.addLog(evmLog)
  }

  override def getAccountStateRoot: Array[Byte] = view.getAccountStateRoot

  override def getListOfForgerStakes: Seq[AccountForgingStakeInfo] = view.getListOfForgerStakes

  override def getForgerStakeData(stakeId: String): Option[ForgerStakeData] = view.getForgerStakeData(stakeId)

  override def getLogs(txHash: Array[Byte]): Array[EvmLog] = view.getLogs(txHash)

  override def getIntermediateRoot: Array[Byte] = view.getIntermediateRoot

  override def nextBaseFee: BigInteger = view.nextBaseFee
}
