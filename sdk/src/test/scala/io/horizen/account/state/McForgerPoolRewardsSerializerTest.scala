package io.horizen.account.state

import io.horizen.account.fixtures.ForgerAccountFixture.getPrivateKeySecp256k1
import io.horizen.account.proposition.AddressProposition
import org.junit.Assert.{assertEquals, fail}
import org.junit.Test

import java.math.BigInteger
import scala.util.{Failure, Success}

class McForgerPoolRewardsSerializerTest {

  @Test
  def serializationRoundTripTest(): Unit = {
    val addr1 = getPrivateKeySecp256k1(1000).publicImage()
    val addr2 = getPrivateKeySecp256k1(1001).publicImage()
    val addr3 = getPrivateKeySecp256k1(1002).publicImage()
    val forgerBlockRewards = Map(
      addr1 -> BigInteger.valueOf(1L),
      addr2 -> BigInteger.valueOf(100L),
      addr3 -> BigInteger.valueOf(9999999L),
    )

    val bytes = McForgerPoolRewardsSerializer.toBytes(forgerBlockRewards)

    McForgerPoolRewardsSerializer.parseBytesTry(bytes) match {
      case Failure(_) => fail("Parsing failed in McForgerPoolRewardsSerializer")
      case Success(value) => assertEquals("Parsed value different from serialized value", value, forgerBlockRewards)
    }
  }

  @Test
  def serializationRoundTripTest_Empty(): Unit = {
    val forgerBlockRewards = Map.empty[AddressProposition, BigInteger]

    val bytes = McForgerPoolRewardsSerializer.toBytes(forgerBlockRewards)

    McForgerPoolRewardsSerializer.parseBytesTry(bytes) match {
      case Failure(_) => fail("Parsing failed in McForgerPoolRewardsSerializer")
      case Success(value) => assertEquals("Parsed value different from serialized value", value, forgerBlockRewards)
    }
  }

}
