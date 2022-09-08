package com.horizen.account.utils

import com.horizen.account.chain.AccountFeePaymentsInfo
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

  def getForgersRewards(blockFeeInfoSeq : Seq[AccountBlockFeeInfo]): Seq[AccountPayment] = {
    var poolFee: BigInteger = BigInteger.ZERO
    val forgersBlockRewards: Seq[AccountPayment] = blockFeeInfoSeq.map(feeInfo => {
      poolFee = poolFee.add(feeInfo.baseFee)
      AccountPayment(feeInfo.forgerAddress, feeInfo.forgerTips)
    })

    // Split poolFee in equal parts to be paid to forgers.
    val forgerPoolFee: BigInteger = poolFee.divide(BigInteger.valueOf(forgersBlockRewards.size))
    // The rest N satoshis must be paid to the first N forgers (1 satoshi each)
    val rest = poolFee.mod(BigInteger.valueOf(forgersBlockRewards.size)).longValue()

    // Calculate final fee for forger considering forger fee, pool fee and the undistributed satoshis
    val allForgersRewards : Seq[AccountPayment] = forgersBlockRewards.zipWithIndex.map {
      case (forgerBlockReward: AccountPayment, index: Int) =>
        val finalForgerFee = forgerBlockReward.value.add(forgerPoolFee).add(if(index < rest) BigInteger.ONE else BigInteger.ZERO)
        AccountPayment(forgerBlockReward.address, finalForgerFee)
    }

    // Aggregate together payments for the same forger
    val forgerKeys: Seq[AddressProposition] = allForgersRewards.map(_.address).distinct

    val forgersRewards: Seq[AccountPayment] = forgerKeys.map {
      forgerKey => { // consider this forger

        // sum all rewards for this forger address
        val forgerTotalFee = allForgersRewards
          .filter(info => forgerKey.equals(info.address))
          .foldLeft(BigInteger.ZERO)((sum, info) => sum.add(info.value))

        // return the resulting entry
        AccountPayment(forgerKey, forgerTotalFee)
      }
    }

    forgersRewards
  }
}
