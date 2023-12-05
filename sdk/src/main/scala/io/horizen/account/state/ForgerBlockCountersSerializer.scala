package io.horizen.account.state

import io.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import sparkz.core.serialization.SparkzSerializer
import sparkz.util.serialization.{Reader, Writer}

object ForgerBlockCountersSerializer extends SparkzSerializer[Map[AddressProposition, Long]] {

  private val addressSerializer: AddressPropositionSerializer = AddressPropositionSerializer.getSerializer

  override def serialize(forgerBlockCounters: Map[AddressProposition, Long], w: Writer): Unit = {
    w.putInt(forgerBlockCounters.size)
    forgerBlockCounters.foreach { case (address, counter) =>
      addressSerializer.serialize(address, w)
      w.putLong(counter)
    }
  }

  override def parse(r: Reader): Map[AddressProposition, Long] = {
    val length = r.getInt()
    (1 to length).map { _ =>
      val address = addressSerializer.parse(r)
      val counter = r.getLong()
      (address, counter)
    }.toMap
  }

}
