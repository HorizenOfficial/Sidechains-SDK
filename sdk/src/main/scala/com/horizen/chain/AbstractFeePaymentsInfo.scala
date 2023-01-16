package com.horizen.chain

import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

trait AbstractFeePaymentsInfo extends BytesSerializable {
  def isEmpty : Boolean
  override def serializer: SparkzSerializer[M]
}