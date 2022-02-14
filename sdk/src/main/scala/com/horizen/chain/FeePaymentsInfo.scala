package com.horizen.chain

import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}
import com.horizen.transaction.FeePaymentsTransaction
import com.horizen.transaction.FeePaymentsTransactionSerializer


case class FeePaymentsInfo(transaction: FeePaymentsTransaction) extends BytesSerializable {
  override type M = FeePaymentsInfo

  override def serializer: ScorexSerializer[M] = FeePaymentsInfoSerializer
}


object FeePaymentsInfoSerializer extends ScorexSerializer[FeePaymentsInfo] {
  override def serialize(feePaymentsInfo: FeePaymentsInfo, w: Writer): Unit = {
    FeePaymentsTransactionSerializer.getSerializer.serialize(feePaymentsInfo.transaction, w)
  }

  override def parse(r: Reader): FeePaymentsInfo = {
    val transaction = FeePaymentsTransactionSerializer.getSerializer.parse(r)

    FeePaymentsInfo(transaction)
  }
}