package com.horizen.account.utils

import com.horizen.account.proposition.AddressProposition
import com.horizen.evm.TrieHasher
import com.horizen.utils.MerkleTree

import java.math.BigInteger

object AccountFeePaymentsUtils {
  val DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH: Array[Byte] = new Array[Byte](MerkleTree.ROOT_HASH_LENGTH)

  def calculateFeePaymentsHash(feePayments: Seq[AccountBlockFeeInfo]): Array[Byte] = {
    if(feePayments.isEmpty) {
      // No fees for the whole epoch, so no fee payments for the Forgers.
      DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH
    } else {
      // turn seq elements into leaves and compute merkel root hash
      TrieHasher.Root(feePayments.map(tx => tx.bytes).toArray)
    }
  }

  def getForgersRewards(blockFeeInfoSeq : Seq[AccountBlockFeeInfo]): Seq[(AddressProposition, BigInteger)] = {
    var poolFee: BigInteger = BigInteger.ZERO
    val forgersBlockRewards: Seq[(AddressProposition, BigInteger)] = blockFeeInfoSeq.map(feeInfo => {
      poolFee = poolFee.add(feeInfo.baseFee)
      (feeInfo.forgerAddress, feeInfo.forgerTips)
    })

    // Split poolFee in equal parts to be paid to forgers.
    val forgerPoolFee: BigInteger = poolFee.divide(BigInteger.valueOf(forgersBlockRewards.size))
    // The rest N satoshis must be paid to the first N forgers (1 satoshi each)
    val rest = poolFee.mod(BigInteger.valueOf(forgersBlockRewards.size)).longValue()

    // Calculate final fee for forger considering forger fee, pool fee and the undistributed satoshis
    val allForgersRewards : Seq[(AddressProposition, BigInteger)] = forgersBlockRewards.zipWithIndex.map {
      case (forgerBlockReward: (AddressProposition, BigInteger), index: Int) =>
        val finalForgerFee = forgerBlockReward._2.add(forgerPoolFee).add(if(index < rest) BigInteger.ONE else BigInteger.ZERO)
        (forgerBlockReward._1, finalForgerFee)
    }

    // Aggregate together payments for the same forger
    val forgerKeys: Seq[AddressProposition] = allForgersRewards.map(_._1).distinct

    val forgersRewards: Seq[(AddressProposition, BigInteger)] = forgerKeys.map {
      forgerKey => { // consider this forger

        // sum all rewards for this forger address
        val forgerTotalFee = allForgersRewards
          .filter(pair => forgerKey.equals(pair._1))
          .foldLeft(BigInteger.ZERO)((sum, pair) => sum.add(pair._2))

        // return the resulting entry
        (forgerKey, forgerTotalFee)
      }
    }

    forgersRewards
  }
}
