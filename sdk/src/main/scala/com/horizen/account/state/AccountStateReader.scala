package com.horizen.account.state

import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.evm.ResourceHandle
import com.horizen.evm.interop.EvmLog
import java.math.BigInteger
import com.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash}


trait AccountStateReader {
  def getStateDbHandle: ResourceHandle

  def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte]

  def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte]

  def accountExists(address: Array[Byte]): Boolean

  def isEoaAccount(address: Array[Byte]): Boolean

  def isSmartContractAccount(address: Array[Byte]): Boolean

  def getNonce(address: Array[Byte]): BigInteger

  def getBalance(address: Array[Byte]): BigInteger

  def getCodeHash(address: Array[Byte]): Array[Byte]

  def getCode(address: Array[Byte]): Array[Byte]

  def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest]

  def getListOfForgersStakes: Seq[AccountForgingStakeInfo]

  def getForgerStakeData(stakeId: String): Option[ForgerStakeData]

  def isForgingOpen: Boolean

  def getAllowedForgerList: Seq[Int]

  def getLogs(txHash: Array[Byte]): Array[EvmLog]

  def getIntermediateRoot: Array[Byte]

  def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys]

  def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof]

  def getCrossChainMessages(withdrawalEpoch: Int): Seq[CrossChainMessage]

  def getCrossChainMessageHashEpoch(msgHash: CrossChainMessageHash): Option[Int]
}
