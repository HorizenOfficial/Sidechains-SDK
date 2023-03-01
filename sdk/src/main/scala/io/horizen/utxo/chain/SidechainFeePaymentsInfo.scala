package io.horizen.utxo.chain

import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.utxo.box.ZenBox
import io.horizen.utxo.transaction.{FeePaymentsTransaction, FeePaymentsTransactionSerializer}
import sparkz.core.serialization.SparkzSerializer
import sparkz.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._


case class SidechainFeePaymentsInfo(transaction: FeePaymentsTransaction) extends AbstractFeePaymentsInfo {
  override type M = SidechainFeePaymentsInfo

  override def serializer: SparkzSerializer[M] = FeePaymentsInfoSerializer

  override def isEmpty: Boolean = transaction.newBoxes().isEmpty
}

object SidechainFeePaymentsInfo {
  def apply(feePayments: Seq[ZenBox]): SidechainFeePaymentsInfo = {
    SidechainFeePaymentsInfo(new FeePaymentsTransaction(feePayments.asJava, FeePaymentsTransaction.FEE_PAYMENTS_TRANSACTION_VERSION))
  }
}


object FeePaymentsInfoSerializer extends SparkzSerializer[SidechainFeePaymentsInfo] {
  override def serialize(feePaymentsInfo: SidechainFeePaymentsInfo, w: Writer): Unit = {
    FeePaymentsTransactionSerializer.getSerializer.serialize(feePaymentsInfo.transaction, w)
  }

  override def parse(r: Reader): SidechainFeePaymentsInfo = {
    val transaction = FeePaymentsTransactionSerializer.getSerializer.parse(r)

    SidechainFeePaymentsInfo(transaction)
  }
}