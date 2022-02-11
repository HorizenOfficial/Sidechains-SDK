package com.horizen.utils

import com.horizen.box.ZenBox
import com.horizen.transaction.FeePaymentsTransaction
import scala.collection.JavaConverters._

object FeePaymentsUtils {
  val DEFAULT_FEE_PAYMENTS_HASH: Array[Byte] = Utils.ZEROS_HASH

  def calculateFeePaymentsHash(feePayments: Seq[ZenBox]): Array[Byte] = {
    if(feePayments.isEmpty) {
      // No fees for the whole epoch, so no fee payments for the Forgers.
      DEFAULT_FEE_PAYMENTS_HASH
    } else {
      // TODO: create FeePaymentsTransaction and return its id() in bytes
      val tx = new FeePaymentsTransaction(feePayments.asJava, FeePaymentsTransaction.FEE_PAYMENTS_TRANSACTION_VERSION)
      BytesUtils.fromHexString(tx.id())
    }
  }
}
