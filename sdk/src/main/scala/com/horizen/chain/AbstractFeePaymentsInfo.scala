package com.horizen.chain

import sparkz.core.serialization.BytesSerializable

trait AbstractFeePaymentsInfo extends BytesSerializable {
  def isEmpty : Boolean
}