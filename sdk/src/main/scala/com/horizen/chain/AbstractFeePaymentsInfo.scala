package com.horizen.chain

import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

trait AbstractFeePaymentsInfo extends BytesSerializable {

  override def serializer: SparkzSerializer[M]
}