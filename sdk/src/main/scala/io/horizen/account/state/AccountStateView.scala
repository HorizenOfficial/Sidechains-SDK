package io.horizen.account.state

import io.horizen.SidechainTypes
import io.horizen.account.fork.Version1_2_0Fork
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.receipt.EthereumReceipt
import io.horizen.account.storage.AccountStateMetadataStorageView
import io.horizen.account.utils._
import io.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import io.horizen.consensus.ConsensusEpochNumber
import io.horizen.state.StateView
import io.horizen.utils.WithdrawalEpochInfo
import io.horizen.evm.StateDB
import sparkz.core.VersionTag
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger

// this class extends 2 main hierarchies, which are kept separate:
//  - StateView (trait): metadata read/write
//      Implements the methods via metadataStorageView
//  - StateDbAccountStateView (concrete class) : evm stateDb read/write
//      Inherits its methods
class AccountStateView(
    metadataStorageView: AccountStateMetadataStorageView,
    stateDb: StateDB,
    messageProcessors: Seq[MessageProcessor]
) extends StateDbAccountStateView(stateDb, messageProcessors)
      with StateView[SidechainTypes#SCAT]
      with SparkzLogging {

  def addTopQualityCertificates(refData: MainchainBlockReferenceData, blockId: ModifierId): Unit = {
    refData.topQualityCertificate.foreach(cert => {
      log.debug(s"adding top quality cert to state: $cert.")
      updateTopQualityCertificate(cert, blockId)
    })
  }

  // out-of-the-box helpers
  override def updateTopQualityCertificate(cert: WithdrawalEpochCertificate, blockId: ModifierId): Unit = {
    metadataStorageView.updateTopQualityCertificate(cert)
    metadataStorageView.updateLastCertificateReferencedEpoch(cert.epochNumber)
    metadataStorageView.updateLastCertificateSidechainBlockIdOpt(blockId)
  }

  override def updateFeePaymentInfo(info: AccountBlockFeeInfo): Unit = {
    metadataStorageView.updateFeePaymentInfo(info)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit =
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit =
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)

  override def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit =
    metadataStorageView.updateTransactionReceipts(receipts)

  def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] =
    metadataStorageView.getTransactionReceipt(txHash)

  def updateNextBaseFee(baseFee: BigInteger): Unit = metadataStorageView.updateNextBaseFee(baseFee)

  def getNextBaseFee: BigInteger = metadataStorageView.getNextBaseFee

  override def setCeased(): Unit = metadataStorageView.setCeased()

  override def commit(version: VersionTag): Unit = {
    // Update StateDB without version, then set the rootHash and commit metadataStorageView
    val rootHash = stateDb.commit()
    metadataStorageView.updateAccountStateRoot(rootHash.toBytes)
    metadataStorageView.commit(version)
  }

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = metadataStorageView.getWithdrawalEpochInfo

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  // useful in bootstrapping tool
  def getConsensusEpochNumberAsInt: Int = getConsensusEpochNumber.getOrElse(0)

  // after this we always reset the counters
  override def getFeePaymentsInfo(
      withdrawalEpoch: Int,
      consensusEpochNumber: ConsensusEpochNumber,
      distributionCap: BigInteger,
      blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None
  ): (Seq[AccountPayment], BigInteger) = {
    var blockFeeInfoSeq = metadataStorageView.getFeePayments(withdrawalEpoch)
    blockToAppendFeeInfo.foreach(blockFeeInfo => blockFeeInfoSeq = blockFeeInfoSeq :+ blockFeeInfo)
    val mcForgerPoolRewards = getMcForgerPoolRewards(consensusEpochNumber, distributionCap)
    val poolBalanceDistributed = mcForgerPoolRewards.values.foldLeft(BigInteger.ZERO)((a, b) => a.add(b))
    metadataStorageView.updateMcForgerPoolRewards(mcForgerPoolRewards)
    (AccountFeePaymentsUtils.getForgersRewards(blockFeeInfoSeq, mcForgerPoolRewards), poolBalanceDistributed)
  }

  override def getAccountStateRoot: Array[Byte] = metadataStorageView.getAccountStateRoot

  def getMcForgerPoolRewards(consensusEpochNumber: ConsensusEpochNumber, distributionCap: BigInteger): Map[AddressProposition, BigInteger] = {
    if (Version1_2_0Fork.get(consensusEpochNumber).active) {
      val extraForgerReward = getBalance(WellKnownAddresses.FORGER_POOL_RECIPIENT_ADDRESS)
      if (extraForgerReward.signum() == 1) {
        val availableReward = extraForgerReward.min(distributionCap)
        val counters: Map[AddressProposition, Long] = getForgerBlockCounters
        val perBlockFee_remainder = availableReward.divideAndRemainder(BigInteger.valueOf(counters.values.sum))
        val perBlockFee = perBlockFee_remainder(0)
        var remainder = perBlockFee_remainder(1)
        //sort and add remainder based by block count
        val forgerPoolRewards = counters.toSeq.sortBy(_._2)
          .map { address_blocks =>
            val blocks = BigInteger.valueOf(address_blocks._2)
            val usedRemainder = remainder.min(blocks)
            val reward = perBlockFee.multiply(blocks).add(usedRemainder)
            remainder = remainder.subtract(usedRemainder)
            (address_blocks._1, reward)
          }
        forgerPoolRewards.toMap
      } else Map.empty
    } else Map.empty
  }

  def updateForgerBlockCounter(forgerPublicKey: AddressProposition, consensusEpochNumber: ConsensusEpochNumber): Unit = {
    if (Version1_2_0Fork.get(consensusEpochNumber).active) {
      metadataStorageView.updateForgerBlockCounter(forgerPublicKey)
    }
  }

  def getForgerBlockCounters: Map[AddressProposition, Long] = {
    metadataStorageView.getForgerBlockCounters
  }

  def subtractForgerPoolBalanceAndResetBlockCounters(consensusEpochNumber: ConsensusEpochNumber, poolBalanceDistributed: BigInteger): Unit = {
    if (Version1_2_0Fork.get(consensusEpochNumber).active) {
      val forgerPoolBalance = getBalance(WellKnownAddresses.FORGER_POOL_RECIPIENT_ADDRESS)
      if (poolBalanceDistributed.compareTo(forgerPoolBalance) > 0) {
        val errMsg = s"Trying to subtract more($poolBalanceDistributed) from the forger pool balance than available($forgerPoolBalance)"
        log.error(errMsg)
        throw new IllegalArgumentException(errMsg)
      }
      if (forgerPoolBalance.signum() == 1) {
        subBalance(WellKnownAddresses.FORGER_POOL_RECIPIENT_ADDRESS, poolBalanceDistributed)
        metadataStorageView.resetForgerBlockCounters()
      }
    }
  }
}
