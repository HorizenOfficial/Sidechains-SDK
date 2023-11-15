package io.horizen.account.state

import io.horizen.account.fixtures.ForgerAccountFixture.getPrivateKeySecp256k1
import org.junit.Assert.{assertEquals, fail}
import org.junit.Test

import scala.util.{Failure, Success}

class ForgerBlockCountersSerializerTest {

  @Test
  def serializationRoundTripTest(): Unit = {
    val addr1 = getPrivateKeySecp256k1(1000).publicImage()
    val addr2 = getPrivateKeySecp256k1(1001).publicImage()
    val addr3 = getPrivateKeySecp256k1(1002).publicImage()
    val forgerBlockCounters = Map(
      addr1 -> 1L,
      addr2 -> 100L,
      addr3 -> 9999999L,
    )

    val bytes = ForgerBlockCountersSerializer.toBytes(forgerBlockCounters)

    ForgerBlockCountersSerializer.parseBytesTry(bytes) match {
      case Failure(_) => fail("Parsing failed in ForgerBlockCountersSerializer")
      case Success(value) => assertEquals("Parsed value different from serialized value", value, forgerBlockCounters)
    }
  }
}
