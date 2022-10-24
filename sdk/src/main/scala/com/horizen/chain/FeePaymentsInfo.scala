package com.horizen.chain

import com.horizen.box.ZenBox
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import scorex.util.serialization.{Reader, Writer}
import com.horizen.transaction.FeePaymentsTransaction
import com.horizen.transaction.serializers.FeePaymentsTransactionSerializer
import scala.collection.JavaConverters._


case class FeePaymentsInfo(transaction: FeePaymentsTransaction) extends BytesSerializable {
  override type M = FeePaymentsInfo

  override def serializer: SparkzSerializer[M] = FeePaymentsInfoSerializer
}

object FeePaymentsInfo {
  def apply(feePayments: Seq[ZenBox]): FeePaymentsInfo = {
    FeePaymentsInfo(new FeePaymentsTransaction(feePayments.asJava, FeePaymentsTransaction.FEE_PAYMENTS_TRANSACTION_VERSION))
  }
}


object FeePaymentsInfoSerializer extends SparkzSerializer[FeePaymentsInfo] {
  override def serialize(feePaymentsInfo: FeePaymentsInfo, w: Writer): Unit = {
    FeePaymentsTransactionSerializer.getSerializer.serialize(feePaymentsInfo.transaction, w)
  }

  override def parse(r: Reader): FeePaymentsInfo = {
    val transaction = FeePaymentsTransactionSerializer.getSerializer.parse(r)

    FeePaymentsInfo(transaction)
  }
}