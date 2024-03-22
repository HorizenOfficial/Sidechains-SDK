package io.horizen.account.utils

import io.horizen.account.proposition.AddressProposition
import io.horizen.evm.{StateDB, TrieHasher}
import io.horizen.params.NetworkParams

import java.math.BigInteger

object AccountFeePaymentsUtils {
  val DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH: Array[Byte] = StateDB.EMPTY_ROOT_HASH.toBytes
  val MC_DISTRIBUTION_CAP_DIVIDER: BigInteger = BigInteger.valueOf(10)

  def calculateFeePaymentsHash(feePayments: Seq[AccountPayment]): Array[Byte] = {
    if(feePayments.isEmpty) {
      // No fees for the whole epoch, so no fee payments for the Forgers.
      DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH
    } else {
      // turn seq elements into leaves and compute merkel root hash
      TrieHasher.Root(feePayments.map(payment => payment.bytes).toArray).toBytes
    }
  }

  def getForgersRewards(blockFeeInfoSeq : Seq[AccountBlockFeeInfo], mcForgerPoolRewards: Map[AddressProposition, BigInteger] = Map.empty): Seq[AccountPayment] = {
    if (blockFeeInfoSeq.isEmpty)
      return mcForgerPoolRewards.map(reward => AccountPayment(reward._1, reward._2)).toSeq

    var poolFee: BigInteger = BigInteger.ZERO
    val forgersBlockRewards: Seq[AccountPayment] = blockFeeInfoSeq.map(feeInfo => {
      poolFee = poolFee.add(feeInfo.baseFee)
      AccountPayment(feeInfo.forgerAddress, feeInfo.forgerTips)
    })

    // Split poolFee in equal parts to be paid to forgers.
    val divAndRem: Array[BigInteger] = poolFee.divideAndRemainder(BigInteger.valueOf(forgersBlockRewards.size))
    val forgerPoolFee: BigInteger = divAndRem(0)
    // The rest N satoshis must be paid to the first N forgers (1 satoshi each)
    val rest: Long = divAndRem(1).longValueExact()

    // Calculate final fee for forger considering forger fee, pool fee and the undistributed satoshis
    val allForgersRewards : Seq[AccountPayment] = forgersBlockRewards.zipWithIndex.map {
      case (forgerBlockReward: AccountPayment, index: Int) =>
        val finalForgerFee = forgerBlockReward.value.add(forgerPoolFee).add(if(index < rest) BigInteger.ONE else BigInteger.ZERO)
        AccountPayment(forgerBlockReward.address, finalForgerFee)
    }

    // Get all unique forger addresses
    val forgerKeys = (allForgersRewards.map(_.address) ++ mcForgerPoolRewards.keys).distinct

    // sum all rewards for per forger address
    forgerKeys.map {
      forgerKey => {
        val forgerTotalFee = allForgersRewards
          .filter(info => forgerKey.equals(info.address))
          .foldLeft(BigInteger.ZERO)((sum, info) => sum.add(info.value))
        // add mcForgerPoolReward if exists
        val mcForgerPoolReward = mcForgerPoolRewards.getOrElse(forgerKey, BigInteger.ZERO)
        // return the resulting entry
        AccountPayment(forgerKey, forgerTotalFee.add(mcForgerPoolReward))
      }
    }
  }

  def getMainchainWithdrawalEpochDistributionCap(epochMaxHeight: Long, params: NetworkParams): BigInteger = {
    val baseReward = 12.5 * 1e8
    val halvingInterval = params.mcHalvingInterval
    val epochLength = params.withdrawalEpochLength

    var mcEpochRewardZennies = 0L
    for (height <- epochMaxHeight - epochLength until epochMaxHeight) {
      var reward = baseReward.longValue()
      val halvings = height / halvingInterval
      for (_ <- 1L to halvings) {
        reward = reward >> 1
      }
      mcEpochRewardZennies = mcEpochRewardZennies + reward
    }

    val mcEpochRewardWei = ZenWeiConverter.convertZenniesToWei(mcEpochRewardZennies)
    mcEpochRewardWei.divide(getMcDistributionCapDivider)
  }

  private def getMcDistributionCapDivider: BigInteger = MC_DISTRIBUTION_CAP_DIVIDER
}
