package io.horizen.account.chain

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.utils.{AccountPayment, AccountPaymentSerializer}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.json.Views
import io.horizen.utils.ListSerializer
import sparkz.core.serialization.SparkzSerializer
import sparkz.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}

@JsonView(Array(classOf[Views.Default]))
case class AccountFeePaymentsInfo(payments: Seq[AccountPayment]) extends AbstractFeePaymentsInfo {
  override type M = AccountFeePaymentsInfo

  override def serializer: SparkzSerializer[M] = AccountFeePaymentsInfoSerializer

  override def isEmpty: Boolean = payments.isEmpty
}


object AccountFeePaymentsInfoSerializer extends SparkzSerializer[AccountFeePaymentsInfo] {

  private val outputsSerializer: ListSerializer[AccountPayment] = new ListSerializer[AccountPayment](AccountPaymentSerializer)

  override def serialize(feePaymentsInfo: AccountFeePaymentsInfo, w: Writer): Unit = {
    outputsSerializer.serialize(feePaymentsInfo.payments.toList.asJava, w)
  }

  override def parse(r: Reader): AccountFeePaymentsInfo = {
    val payments : java.util.List[AccountPayment] = outputsSerializer.parse(r)
    AccountFeePaymentsInfo(payments.asScala)
  }
}