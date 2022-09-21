package com.horizen.chain

import scorex.core.serialization.{BytesSerializable, ScorexSerializer}

trait AbstractFeePaymentsInfo extends BytesSerializable {

  override def serializer: ScorexSerializer[M]
}