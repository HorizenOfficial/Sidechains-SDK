package io.horizen.utxo.companion

import com.google.common.primitives.Bytes
import io.horizen.SidechainTypes
import io.horizen.utxo.box.BoxSerializer
import io.horizen.utxo.customtypes.{CustomBox, CustomBoxSerializer}
import io.horizen.utxo.fixtures.BoxFixture
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

class SidechainBoxesCompanionTest
  extends JUnitSuite
  with BoxFixture
  with SidechainTypes
{

  var customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])

  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers, false)
  val sidechainBoxesCompanionCore = SidechainBoxesCompanion(new JHashMap(), false)

  @Test
  def testCore(): Unit = {
    // Test 1: ZenBox serialization/deserialization
    val zenBox = getZenBox

    val zenBoxBytes = sidechainBoxesCompanion.toBytes(zenBox)

    assertEquals("Type of serialized box must be ZenBox.", zenBox.boxTypeId(), zenBoxBytes(0))
    assertEquals("Deserialization must restore same box.", zenBox, sidechainBoxesCompanion.parseBytesTry(zenBoxBytes).get)


    // Test 2: WithdrawalRequestBox serialization/deserialization
    val withdrawalRequestBox = getWithdrawalRequestBox

    val withdrawalRequestBoxBytes = sidechainBoxesCompanion.toBytes(withdrawalRequestBox)

    assertEquals("Type of serialized box must be WithdrawalRequestBox.", withdrawalRequestBox.boxTypeId(), withdrawalRequestBoxBytes(0))
    assertEquals("Deserialization must restore same box.", withdrawalRequestBox, sidechainBoxesCompanion.parseBytesTry(withdrawalRequestBoxBytes).get)


    // Test 3: ForgerBox serialization/deserialization
    val forgerBox = getForgerBox

    val forgerBoxBytes = sidechainBoxesCompanion.toBytes(forgerBox)

    assertEquals("Type of serialized box must be ForgerBox.", forgerBox.boxTypeId(), forgerBoxBytes(0))
    assertEquals("Deserialization must restore same box.", forgerBox, sidechainBoxesCompanion.parseBytesTry(forgerBoxBytes).get)
  }

  @Test
  def testRegisteredCustom(): Unit = {
    val customBox = getCustomBox.asInstanceOf[SidechainTypes#SCB]

    val customBoxBytes = sidechainBoxesCompanion.toBytes(customBox)
    assertEquals("Box type must be custom.", Byte.MaxValue, customBoxBytes(0))
    assertEquals("Type of serialized box must be CustomBox.", customBox.boxTypeId(), customBoxBytes(1))
    assertEquals("Deserialization must restore same box.", customBox, sidechainBoxesCompanion.parseBytesTry(customBoxBytes).get)
  }

  @Test
  def testUnregisteredCustom(): Unit = {
    val customBox = getCustomBox.asInstanceOf[SidechainTypes#SCB]
    var exceptionThrown = false


    // Test 1: try to serialize custom type Box. Serialization exception expected, because of custom type is unregistered.
    try {
      sidechainBoxesCompanionCore.toBytes(customBox)
    } catch {
      case _ : Throwable => exceptionThrown = true
    }

    assertTrue("Exception must be thrown for unregistered box type.", exceptionThrown)


    // Test 2: try to deserialize custom type Box. Serialization exception expected, because of custom type is unregistered.
    exceptionThrown = false
    val customBoxBytes = sidechainBoxesCompanion.toBytes(customBox)

    try {
      sidechainBoxesCompanionCore.parseBytesTry(customBoxBytes).get
    } catch {
      case _ : Throwable => exceptionThrown = true
    }

    assertTrue("Exception must be thrown for unregistered box type.", exceptionThrown)
  }


  @Test
  def testSpuriousBytes(): Unit = {
    // Test 1: ZenBox deserialization
    val zenBox = getZenBox
    val zenBoxBytes = Bytes.concat(sidechainBoxesCompanion.toBytes(zenBox), new Array[Byte](1))

    assertEquals("Type of serialized box must be ZenBox.", zenBox.boxTypeId(), zenBoxBytes(0))
    val exception1 = intercept[IllegalArgumentException](
     sidechainBoxesCompanion.parseBytesTry(zenBoxBytes).get
    )
    assertTrue(exception1.getMessage.contains("Spurious bytes found"))

    // Test 2: WithdrawalRequestBox serialization/deserialization
    val withdrawalRequestBox = getWithdrawalRequestBox
    val withdrawalRequestBoxBytes = Bytes.concat(sidechainBoxesCompanion.toBytes(withdrawalRequestBox), new Array[Byte](1))

    assertEquals("Type of serialized box must be WithdrawalRequestBox.", withdrawalRequestBox.boxTypeId(), withdrawalRequestBoxBytes(0))
    val exception2 = intercept[IllegalArgumentException](
      sidechainBoxesCompanion.parseBytesTry(withdrawalRequestBoxBytes).get
    )
    assertTrue(exception2.getMessage.contains("Spurious bytes found"))

    // Test 3: ForgerBox serialization/deserialization
    val forgerBox = getForgerBox
    val forgerBoxBytes = Bytes.concat(sidechainBoxesCompanion.toBytes(forgerBox), new Array[Byte](1))

    assertEquals("Type of serialized box must be ForgerBox.", forgerBox.boxTypeId(), forgerBoxBytes(0))
    val exception3 = intercept[IllegalArgumentException](
      sidechainBoxesCompanion.parseBytesTry(forgerBoxBytes).get
    )
    assertTrue(exception3.getMessage.contains("Spurious bytes found"))
  }
}