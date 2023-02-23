package com.horizen.account.utils

import io.horizen.evm.{StateDB, TrieHasher}

import java.math.BigInteger

object AccountFeePaymentsUtils {
  val DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH: Array[Byte] = StateDB.EMPTY_ROOT_HASH.toBytes

  def calculateFeePaymentsHash(feePayments: Seq[AccountPayment]): Array[Byte] = {
    if(feePayments.isEmpty) {
      // No fees for the whole epoch, so no fee payments for the Forgers.
      DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH
    } else {
      // turn seq elements into leaves and compute merkel root hash
      TrieHasher.Root(feePayments.map(payment => payment.bytes).toArray).toBytes
    }
  }

  def getForgersRewards(blockFeeInfoSeq : Seq[AccountBlockFeeInfo]): Seq[AccountPayment] = {
    if (blockFeeInfoSeq.isEmpty)
      return Seq()

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

    // Aggregate together payments for the same forger
    val forgerKeys = allForgersRewards.map(_.address).distinct

    // sum all rewards for per forger address
    forgerKeys.map {
      forgerKey => {
        val forgerTotalFee = allForgersRewards
          .filter(info => forgerKey.equals(info.address))
          .foldLeft(BigInteger.ZERO)((sum, info) => sum.add(info.value))
        // return the resulting entry
        AccountPayment(forgerKey, forgerTotalFee)
      }
    }
  }
}
