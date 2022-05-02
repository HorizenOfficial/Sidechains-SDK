package com.horizen.companion

import org.scalatestplus.junit.JUnitSuite
import org.junit.Test
import org.junit.Assert._
import com.horizen.fixtures._
import com.horizen.customtypes._
import com.horizen.box._
import com.horizen.proposition._
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes

class SidechainBoxesCompanionTest
  extends JUnitSuite
  with BoxFixture
  with SidechainTypes
{

  var customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])

  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers)
  val sidechainBoxesCompanionCore = SidechainBoxesCompanion(new JHashMap())

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
}