package com.horizen.companion

import scala.collection.mutable._

import org.scalatest.junit.JUnitSuite

import org.junit.Test
import org.junit.Assert._

import com.horizen.fixtures._
import com.horizen.customtypes._
import com.horizen.box._
import com.horizen.proposition._

class SidechainBoxesCompanionTest
  extends JUnitSuite
    with BoxFixture
{

  val customBoxesSerializers: Map[Byte, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(CustomBox.BOX_TYPE_ID -> CustomBoxSerializer.getSerializer)
  val sidechainBoxesCompanion = new SidechainBoxesCompanion(customBoxesSerializers)
  val sidechainBoxesCompanionCore = new SidechainBoxesCompanion(Map())

  @Test def testCore(): Unit = {
    //Test for RegularBox
    val rb = getRegularBox()

    val rbb = sidechainBoxesCompanion.toBytes(rb)

    assertEquals("Type of serialized box must be RegularBox.", rb.boxTypeId(), rbb(0))
    assertEquals("Deserialization must restore same box.", rb, sidechainBoxesCompanion.parseBytes(rbb).get)

    //Test for CertifierRightBox
    val crb = getCertifierRightBox()

    val crbb = sidechainBoxesCompanion.toBytes(crb)

    assertEquals("Type of serialized box must be CertifierRightBox.", crb.boxTypeId(), crbb(0))
    assertEquals("Deserialization must restore same box.", crb, sidechainBoxesCompanion.parseBytes(crbb).get)
  }

  @Test def testCustom(): Unit = {
    val cb = getCustomBox()
    var exceptionThrown = false

    //Test serialization exception if custom type is unregistered
    try {
      val cbb = sidechainBoxesCompanionCore.toBytes(cb)
    } catch {
      case e : IllegalArgumentException => exceptionThrown = true
    }

    assertTrue("Exception must be thrown for unregistered box type.", exceptionThrown)

    //Test no exception if custom type is registered
    exceptionThrown = false

    val cbb = sidechainBoxesCompanion.toBytes(cb)

    assertEquals("Box type must be custom.", Byte.MaxValue, cbb(0))
    assertEquals("Type of serialized box must be CustomBox.", cb.boxTypeId(), cbb(1))
    assertEquals("Deserialization must restore same box.", cb, sidechainBoxesCompanion.parseBytes(cbb).get)

    //Test serialization exception if custom type is unregistered
    try {
      sidechainBoxesCompanionCore.parseBytes(cbb).get
    } catch {
      case e : MatchError => exceptionThrown = true
    }

    assertTrue("Exception must be thrown for unregistered box type.", exceptionThrown)
  }
}