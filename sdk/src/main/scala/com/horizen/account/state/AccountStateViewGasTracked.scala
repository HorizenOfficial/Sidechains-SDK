package com.horizen.account.state

import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.evm.ResourceHandle
import com.horizen.evm.interop.EvmLog
import java.math.BigInteger
import com.horizen.sc2sc.{CrossChainMessageHash, CrossChainMessage}

/**
 * Wrapper for AccountStateView to help with tracking gas consumption.
 * @param view
 *   instance of BaseAccountStateView
 * @param gas
 *   GasPool instance to deduct gas from
 */
class AccountStateViewGasTracked(view: BaseAccountStateView, gas: GasPool) extends BaseAccountStateView {
  override def getStateDbHandle: ResourceHandle = view.getStateDbHandle

  override def addAccount(address: Array[Byte], codeHash: Array[Byte]): Unit = view.addAccount(address, codeHash)

  override def accountExists(address: Array[Byte]): Boolean = {
    gas.subGas(GasUtil.ExtcodeHashGasEIP1884)
    view.accountExists(address)
  }

  @throws(classOf[OutOfGasException])
  override def isEoaAccount(address: Array[Byte]): Boolean = {
    gas.subGas(GasUtil.ExtcodeHashGasEIP1884)
    view.isEoaAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def isSmartContractAccount(address: Array[Byte]): Boolean = {
    gas.subGas(GasUtil.ExtcodeHashGasEIP1884)
    view.isSmartContractAccount(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCodeHash(address: Array[Byte]): Array[Byte] = {
    gas.subGas(GasUtil.ExtcodeHashGasEIP1884)
    view.getCodeHash(address)
  }

  @throws(classOf[OutOfGasException])
  override def getCode(address: Array[Byte]): Array[Byte] = {
    gas.subGas(GasUtil.ExtcodeHashGasEIP1884)
    view.getCode(address)
  }

  override def getNonce(address: Array[Byte]): BigInteger = view.getNonce(address)

  override def increaseNonce(address: Array[Byte]): Unit = view.increaseNonce(address)

  @throws(classOf[OutOfGasException])
  override def getBalance(address: Array[Byte]): BigInteger = {
    gas.subGas(GasUtil.BalanceGasEIP1884)
    view.getBalance(address)
  }

  @throws(classOf[OutOfGasException])
  override def addBalance(address: Array[Byte], amount: BigInteger): Unit = {
    gas.subGas(GasUtil.BalanceGasEIP1884)
    view.addBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def subBalance(address: Array[Byte], amount: BigInteger): Unit = {
    gas.subGas(GasUtil.BalanceGasEIP1884)
    view.subBalance(address, amount)
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    gas.subGas(GasUtil.GasTBD)
    view.getAccountStorage(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    gas.subGas(GasUtil.GasTBD)
    view.getAccountStorageBytes(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    gas.subGas(GasUtil.GasTBD)
    view.updateAccountStorage(address, key, value)
  }

  @throws(classOf[OutOfGasException])
  override def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    gas.subGas(GasUtil.GasTBD)
    view.updateAccountStorageBytes(address, key, value)
  }

  @throws(classOf[OutOfGasException])
  override def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Unit = {
    gas.subGas(GasUtil.GasTBD)
    view.removeAccountStorage(address, key)
  }

  @throws(classOf[OutOfGasException])
  override def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Unit = {
    gas.subGas(GasUtil.GasTBD)
    view.removeAccountStorageBytes(address, key)
  }


  @throws(classOf[OutOfGasException])
  override def addLog(evmLog: EvmLog): Unit = {
    gas.subGas(GasUtil.logGas(evmLog))
    view.addLog(evmLog)
  }

  override def getListOfForgersStakes: Seq[AccountForgingStakeInfo] = view.getListOfForgersStakes

  override def getForgerStakeData(stakeId: String): Option[ForgerStakeData] = view.getForgerStakeData(stakeId)

  override def getLogs(txHash: Array[Byte]): Array[EvmLog] = view.getLogs(txHash)

  override def getIntermediateRoot: Array[Byte] = view.getIntermediateRoot

  override def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest] = view.getWithdrawalRequests(withdrawalEpoch)

  override def isForgingOpen: Boolean = view.isForgingOpen

  override def getAllowedForgerList: Seq[Int] = view.getAllowedForgerList

  override def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys] = view.certifiersKeys(withdrawalEpoch)

  override def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof] = view.keyRotationProof(withdrawalEpoch, indexOfSigner, keyType)

  override def getCrossChainMessages(withdrawalEpoch: Int): Seq[CrossChainMessage] = view.getCrossChainMessages(withdrawalEpoch)

  override def getCrossChainMessageHashEpoch(msgHash: CrossChainMessageHash): Option[Int] = view.getCrossChainMessageHashEpoch(msgHash)
}
