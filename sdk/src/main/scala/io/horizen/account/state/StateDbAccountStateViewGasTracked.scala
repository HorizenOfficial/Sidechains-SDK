package io.horizen.account.state

import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.evm.{Address, Hash, StateDB}

import java.math.BigInteger

/**
 * Extension to help with tracking gas consumption.
 * @param gas
 *   GasPool instance to deduct gas from
 */
class StateDbAccountStateViewGasTracked(
    stateDb: StateDB,
    messageProcessors: Seq[MessageProcessor],
    readOnly: Boolean,
    gas: GasPool
) extends StateDbAccountStateView(stateDb, messageProcessors, readOnly) {

  /**
   * Consume gas for account access:
   *   - charge ColdAccountAccessCostEIP2929 if account was not accessed yet
   *   - charge WarmStorageReadCostEIP2929 if account was already accessed
   */
  @throws(classOf[OutOfGasException])
  private def accountAccess(address: Address): Unit = {
    val warm = stateDb.accessAccount(address)
    gas.subGas(if (warm) GasUtil.WarmStorageReadCostEIP2929 else GasUtil.ColdAccountAccessCostEIP2929)
  }

  /**
   * Consume for account storage access:
   *   - charge ColdSloadCostEIP2929 if account storage slot was not accessed yet
   *   - charge WarmStorageReadCostEIP2929 if account storage slot was already accessed
   */
  @throws(classOf[OutOfGasException])
  private def storageAccess(address: Address, slot: Array[Byte]): Unit = {
    val warm = stateDb.accessSlot(address, new Hash(slot))
    gas.subGas(if (warm) GasUtil.WarmStorageReadCostEIP2929 else GasUtil.ColdSloadCostEIP2929)
  }

  /**
   * Consume gas for SSTORE according to EIP-3529.
   * @see
   *   original implementation in GETH: github.com/ethereum/go-ethereum@v1.10.26/core/vm/operations_acl.go:27
   */
  @throws(classOf[OutOfGasException])
  private def storageWriteAccess(address: Address, key: Hash, value: Hash): Unit = {
    // If we fail the minimum gas availability invariant, fail (0)
    if (gas.getGas.compareTo(GasUtil.SstoreSentryGasEIP2200) <= 0)
      throw new OutOfGasException("account storage write gas sentry fail")
    // Gas sentry honoured, do the actual gas calculation based on the stored value
    if (!stateDb.accessSlot(address, key)) {
      gas.subGas(GasUtil.ColdSloadCostEIP2929)
    }
    val writeGasCost = {
      val current = stateDb.getStorage(address, key)
      if (value.equals(current)) {
        // noop (1)
        GasUtil.WarmStorageReadCostEIP2929
      } else {
        val original = stateDb.getCommittedStorage(address, key)
        if (original.equals(current)) {
          if (original.equals(Hash.ZERO)) {
            // create slot (2.1.1)
            GasUtil.SstoreSetGasEIP2200
          } else {
            if (value.equals(Hash.ZERO)) {
              // delete slot (2.1.2b)
              stateDb.addRefund(GasUtil.SstoreClearsScheduleRefundEIP3529)
            }
            // write existing slot (2.1.2)
            GasUtil.SstoreResetGasEIP2200.subtract(GasUtil.ColdSloadCostEIP2929)
          }
        } else {
          if (!original.equals(Hash.ZERO)) {
            if (current.equals(Hash.ZERO)) {
              // recreate slot (2.2.1.1)
              stateDb.subRefund(GasUtil.SstoreClearsScheduleRefundEIP3529)
            } else if (value.equals(Hash.ZERO)) {
              // delete slot (2.2.1.2)
              stateDb.addRefund(GasUtil.SstoreClearsScheduleRefundEIP3529)
            }
          }
          if (original.equals(value)) {
            if (original.equals(Hash.ZERO)) {
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

  override def accountExists(address: Address): Boolean = {
    accountAccess(address)
    super.accountExists(address)
  }

  @throws(classOf[OutOfGasException])
  override def isEoaAccount(address: Address): Boolean = {
    accountAccess(address)
    super.isEoaAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def isSmartContractAccount(address: Address): Boolean = {
    accountAccess(address)
    super.isSmartContractAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCodeHash(address: Address): Array[Byte] = {
    accountAccess(address)
    super.getCodeHash(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCode(address: Address): Array[Byte] = {
    accountAccess(address)
    val code = super.getCode(address)
    if (code != null) {
      // consume additional gas proportional to the code size:
      // this should preferably be done before acutally copying the code,
      // but currently we don't have a cheaper way to find out the size beforehand
      gas.subGas(GasUtil.codeCopy(code.length))
    }
    code
  }

  override def getNonce(address: Address): BigInteger = {
    accountAccess(address)
    super.getNonce(address)
  }

  override def increaseNonce(address: Address): Unit = {
    accountAccess(address)
    super.increaseNonce(address)
  }

  @throws(classOf[OutOfGasException])
  override def getBalance(address: Address): BigInteger = {
    accountAccess(address)
    super.getBalance(address)
  }

  @throws(classOf[OutOfGasException])
  override def addBalance(address: Address, amount: BigInteger): Unit = {
    accountAccess(address)
    super.addBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def subBalance(address: Address, amount: BigInteger): Unit = {
    accountAccess(address)
    super.subBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorage(address: Address, key: Array[Byte]): Array[Byte] = {
    storageAccess(address, key)
    super.getAccountStorage(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorage(address: Address, key: Array[Byte], value: Array[Byte]): Unit = {
    storageWriteAccess(address, new Hash(key), new Hash(value))
    super.updateAccountStorage(address, key, value)
  }

  @throws(classOf[OutOfGasException])
  override def addLog(log: EthereumConsensusDataLog): Unit = {
    gas.subGas(GasUtil.logGas(log))
    super.addLog(log)
  }
}
