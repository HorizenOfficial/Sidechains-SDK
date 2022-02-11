package com.horizen.utils

import com.horizen.SidechainTypes
import com.horizen.cryptolibprovider.FieldElementUtils

object FeePaymentsUtils {
  val DEFAULT_FEE_PAYMENTS_HASH: Array[Byte] = Utils.ZEROS_HASH

  def calculateFeePaymentsHash(feePayments: Seq[SidechainTypes#SCB]): Array[Byte] = {
    if(feePayments.isEmpty) {
      // No fees for the whole epoch, so no fee payments for the Forgers.
      DEFAULT_FEE_PAYMENTS_HASH
    } else {
      // TODO: create FeePaymentsTransaction and return its id() in bytes
      FieldElementUtils.randomFieldElementBytes(feePayments.size)
    }
  }
}
