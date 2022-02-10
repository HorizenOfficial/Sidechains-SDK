package com.horizen.utils

import com.horizen.SidechainTypes
import com.horizen.cryptolibprovider.FieldElementUtils

object FeePaymentsUtils {
  def calculateFeePaymentsHash(feePayments: Seq[SidechainTypes#SCB]): Array[Byte] = {
    if(feePayments.isEmpty) {
      // No fees for the whole epoch, so no fee payments for the Forgers.
      Utils.ZEROS_HASH
    } else {
      // TODO: create FeePaymentsTransaction and return its id() in bytes
      FieldElementUtils.randomFieldElementBytes(feePayments.size)
    }
  }
}
