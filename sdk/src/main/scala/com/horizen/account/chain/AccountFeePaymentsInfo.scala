package com.horizen.account.chain

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.account.utils.{AccountPayment, AccountPaymentSerializer}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.serialization.Views
import com.horizen.utils.ListSerializer
import scorex.core.serialization.ScorexSerializer
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}

@JsonView(Array(classOf[Views.Default]))
case class AccountFeePaymentsInfo(payments: Seq[AccountPayment]) extends AbstractFeePaymentsInfo {
  override type M = AccountFeePaymentsInfo

  override def serializer: ScorexSerializer[M] = AccountFeePaymentsInfoSerializer
}


object AccountFeePaymentsInfoSerializer extends ScorexSerializer[AccountFeePaymentsInfo] {

  private val outputsSerializer: ListSerializer[AccountPayment] = new ListSerializer[AccountPayment](AccountPaymentSerializer.getSerializer)

  override def serialize(feePaymentsInfo: AccountFeePaymentsInfo, w: Writer): Unit = {
    outputsSerializer.serialize(feePaymentsInfo.payments.toList.asJava, w)
  }

  override def parse(r: Reader): AccountFeePaymentsInfo = {
    val payments : java.util.List[AccountPayment] = outputsSerializer.parse(r)
    AccountFeePaymentsInfo(payments.asScala)
  }
}