package com.horizen.account.state

import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.evm.StateDB
import com.horizen.evm.interop.EvmLog

import java.math.BigInteger

/**
 * Wrapper for AccountStateView to help with tracking gas consumption.
 * @param gas
 *   GasPool instance to deduct gas from
 */
class AccountStateViewGasTracked(
    metadataStorageView: AccountStateMetadataStorageView,
    stateDb: StateDB,
    messageProcessors: Seq[MessageProcessor],
    gas: GasPool
) extends AccountStateView(metadataStorageView, stateDb, messageProcessors) {

  /**
   * Consume gas for account access:
   *   - charge ColdAccountAccessCostEIP2929 if account was not accessed yet
   *   - charge WarmStorageReadCostEIP2929 if account was already accessed
   */
  @throws(classOf[OutOfGasException])
  private def accountAccess(address: Array[Byte]): Unit = {
    val warm = stateDb.accessAccount(address)
    gas.subGas(if (warm) GasUtil.WarmStorageReadCostEIP2929 else GasUtil.ColdAccountAccessCostEIP2929)
  }

  /**
   * Consume for account storage access:
   *   - charge ColdSloadCostEIP2929 if account storage slot was not accessed yet
   *   - charge WarmStorageReadCostEIP2929 if account storage slot was already accessed
   */
  @throws(classOf[OutOfGasException])
  private def slotRead(address: Array[Byte], slot: Array[Byte]): Unit = {
    val warm = stateDb.accessSlot(address, slot)
    gas.subGas(if (warm) GasUtil.WarmStorageReadCostEIP2929 else GasUtil.ColdSloadCostEIP2929)
  }

  override def accountExists(address: Array[Byte]): Boolean = {
    accountAccess(address)
    super.accountExists(address)
  }

  @throws(classOf[OutOfGasException])
  override def isEoaAccount(address: Array[Byte]): Boolean = {
    accountAccess(address)
    super.isEoaAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def isSmartContractAccount(address: Array[Byte]): Boolean = {
    accountAccess(address)
    super.isSmartContractAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCodeHash(address: Array[Byte]): Array[Byte] = {
    accountAccess(address)
    super.getCodeHash(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCode(address: Array[Byte]): Array[Byte] = {
    accountAccess(address)
    val code = super.getCode(address)
    // cosume additional gas proportional to the code size,
    // this should preferably be done before acutally copying the code, but we don't know the size beforehand
    gas.subGas(GasUtil.codeCopy(code.length))
    code
  }

  override def getNonce(address: Array[Byte]): BigInteger = {
    accountAccess(address)
    super.getNonce(address)
  }

  override def increaseNonce(address: Array[Byte]): Unit = {
    accountAccess(address)
    super.increaseNonce(address)
  }

  @throws(classOf[OutOfGasException])
  override def getBalance(address: Array[Byte]): BigInteger = {
    accountAccess(address)
    super.getBalance(address)
  }

  @throws(classOf[OutOfGasException])
  override def addBalance(address: Array[Byte], amount: BigInteger): Unit = {
    accountAccess(address)
    super.addBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def subBalance(address: Array[Byte], amount: BigInteger): Unit = {
    accountAccess(address)
    super.subBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    slotRead(address, key)
    super.getAccountStorage(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    // TODO: complicated SSTORE gas usage + refunds handling
    //  see /home/gigo/go/pkg/mod/github.com/ethereum/go-ethereum@v1.10.26/core/vm/operations_acl.go:27
//    gas.subGas(GasUtil.GasTBD)
    super.updateAccountStorage(address, key, value)
  }

  @throws(classOf[OutOfGasException])
  override def addLog(evmLog: EvmLog): Unit = {
    gas.subGas(GasUtil.logGas(evmLog))
    super.addLog(evmLog)
  }
}
