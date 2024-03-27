package io.horizen.account.state

import io.horizen.account.state.nativescdata.forgerstakev2.{StakeDataDelegator, StakeDataForger}
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.utils.AccountPayment
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import io.horizen.evm.{Address, ResourceHandle}

import java.math.BigInteger

trait AccountStateReader {
  def getStateDbHandle: ResourceHandle
  def getAccountStorage(address: Address, key: Array[Byte]): Array[Byte]
  def getAccountStorageBytes(address: Address, key: Array[Byte]): Array[Byte]

  def accountExists(address: Address): Boolean
  def isEoaAccount(address: Address): Boolean
  def isSmartContractAccount(address: Address): Boolean

  def getNonce(address: Address): BigInteger
  def getBalance(address: Address): BigInteger
  def getCodeHash(address: Address): Array[Byte]
  def getCode(address: Address): Array[Byte]

  def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest]

  def getListOfForgersStakes(isForkV1_3Active: Boolean): Seq[AccountForgingStakeInfo]
  def getPagedListOfForgersStakes(startPos: Int, pageSize: Int): (Int, Seq[AccountForgingStakeInfo])
  def getPagedForgersStakesByForger(forger: ForgerPublicKeys, startPos: Int, pageSize: Int): (Int, Seq[StakeDataDelegator])
  def getPagedForgersStakesByDelegator(delegator: Address, startPos: Int, pageSize: Int): (Int, Seq[StakeDataForger])
 
  def getForgerStakeData(stakeId: String, isForkV1_3Active: Boolean): Option[ForgerStakeData]
  def isForgingOpen: Boolean
  def isForgerStakeAvailable(isForkV1_3Active: Boolean): Boolean
  def getAllowedForgerList: Seq[Int]

  def getListOfMcAddrOwnerships(scAddress: Option[String] = None): Seq[McAddrOwnershipData]
  def getListOfOwnerScAddresses(): Seq[OwnerScAddress]
  def ownershipDataExist(ownershipId: Array[Byte]): Boolean


  def getLogs(txHash: Array[Byte]): Array[EthereumConsensusDataLog]
  def getIntermediateRoot: Array[Byte]

  def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys]
  def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof]
}
