package io.horizen.account.state

import io.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import io.horizen.evm.utils.BigIntegerSerializer
import sparkz.core.serialization.SparkzSerializer
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger

object McForgerPoolRewardsSerializer extends SparkzSerializer[Map[AddressProposition, BigInteger]] {

  private val addressSerializer: AddressPropositionSerializer = AddressPropositionSerializer.getSerializer
  new BigIntegerSerializer()

  override def serialize(forgerPoolRewards: Map[AddressProposition, BigInteger], w: Writer): Unit = {
    w.putInt(forgerPoolRewards.size)
    forgerPoolRewards.foreach { case (address, reward) =>
      addressSerializer.serialize(address, w)
      val rewardBytes: Array[Byte] = reward.toByteArray
      w.putInt(rewardBytes.length)
      w.putBytes(rewardBytes)
    }
  }

  override def parse(r: Reader): Map[AddressProposition, BigInteger] = {
    val length = r.getInt()
    (1 to length).map { _ =>
      val address = addressSerializer.parse(r)
      val rewardLength: Int = r.getInt
      val reward: BigInteger = new BigInteger(r.getBytes(rewardLength))
      (address, reward)
    }.toMap
  }

}
