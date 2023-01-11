package com.horizen.account.state

import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.evm.StateDB
import com.horizen.evm.interop.EvmLog

import java.math.BigInteger
import java.util

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
  private def storageAccess(address: Array[Byte], slot: Array[Byte]): Unit = {
    val warm = stateDb.accessSlot(address, slot)
    gas.subGas(if (warm) GasUtil.WarmStorageReadCostEIP2929 else GasUtil.ColdSloadCostEIP2929)
  }

  /**
   * Implements gas cost for SSTORE according to EIP-3529.
   * @see
   *   github.com/ethereum/go-ethereum@v1.10.26/core/vm/operations_acl.go:27
   * @see
   *   For test cases see EIP-3529 document:
   *   https://github.com/ethereum/EIPs/blob/master/EIPS/eip-3529.md#with-reduced-refunds
   */
  @throws(classOf[OutOfGasException])
  private def storageWriteAccess(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    // If we fail the minimum gas availability invariant, fail (0)
    if (gas.getGas.compareTo(GasUtil.SstoreSentryGasEIP2200) <= 0) throw new OutOfGasException()
    // Gas sentry honoured, do the actual gas calculation based on the stored value
    if (!stateDb.accessSlot(address, key)) {
      gas.subGas(GasUtil.ColdSloadCostEIP2929)
    }
    val writeGasCost = {
      val current = stateDb.getStorage(address, key)
      if (util.Arrays.equals(value, current)) {
        // noop (1)
        GasUtil.WarmStorageReadCostEIP2929
      } else {
        val original = stateDb.getCommittedStorage(address, key)
        if (util.Arrays.equals(original, current)) {
          if (original.forall(_ == 0)) {
            // create slot (2.1.1)
            GasUtil.SstoreSetGasEIP2200
          } else {
            if (value.forall(_ == 0)) {
              // delete slot (2.1.2b)
              stateDb.addRefund(GasUtil.SstoreClearsScheduleRefundEIP3529)
            }
            // write existing slot (2.1.2)
            GasUtil.SstoreResetGasEIP2200.subtract(GasUtil.ColdSloadCostEIP2929)
          }
        } else {
          if (!original.forall(_ == 0)) {
            if (current.forall(_ == 0)) {
              // recreate slot (2.2.1.1)
              stateDb.subRefund(GasUtil.SstoreClearsScheduleRefundEIP3529)
            } else if (value.forall(_ == 0)) {
              // delete slot (2.2.1.2)
              stateDb.addRefund(GasUtil.SstoreClearsScheduleRefundEIP3529)
            }
          }
          if (util.Arrays.equals(original, value)) {
            if (original.forall(_ == 0)) {
              // reset to original inexistent slot (2.2.2.1)
              stateDb.addRefund(GasUtil.SstoreSetGasEIP2200.subtract(GasUtil.WarmStorageReadCostEIP2929))
            } else {
              // reset to original existing slot (2.2.2.2)
              stateDb.addRefund(
                GasUtil.SstoreResetGasEIP2200
                  .subtract(GasUtil.ColdSloadCostEIP2929)
                  .subtract(GasUtil.WarmStorageReadCostEIP2929)
              )
            }
          }
          // dirty update (2.2)
          GasUtil.WarmStorageReadCostEIP2929
        }
      }
    }
    // consume gas
    gas.subGas(writeGasCost)
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
    // cosume additional gas proportional to the code size:
    // this should preferably be done before acutally copying the code,
    // but currently we don't have a cheaper way to find out the size beforehand
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
    storageAccess(address, key)
    super.getAccountStorage(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    storageWriteAccess(address, key, value)
    super.updateAccountStorage(address, key, value)
  }

  @throws(classOf[OutOfGasException])
  override def addLog(evmLog: EvmLog): Unit = {
    gas.subGas(GasUtil.logGas(evmLog))
    super.addLog(evmLog)
  }
}
