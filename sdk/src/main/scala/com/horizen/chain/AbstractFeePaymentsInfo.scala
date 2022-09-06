package com.horizen.chain

import scorex.core.serialization.{BytesSerializable, ScorexSerializer}

abstract class AbstractFeePaymentsInfo() extends BytesSerializable {

  override def serializer: ScorexSerializer[M]
}