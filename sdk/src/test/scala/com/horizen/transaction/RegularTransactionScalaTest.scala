package com.horizen.transaction

import java.util.{ArrayList => JArrayList, List => JList}

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.utils.{Pair => JPair}
import com.horizen.box.{Box, ZenBox}
import com.horizen.box.data.{BoxData, ZenBoxData}
import com.horizen.fixtures.BoxFixture
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.serialization.ApplicationJsonSerializer
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import scorex.core.utils.ScorexEncoder

class RegularTransactionScalaTest extends JUnitSuite with BoxFixture
{

  @Test
  def testToJson(): Unit = {
    val fee = 10

    val from = new JArrayList[JPair[ZenBox, PrivateKey25519]]
    val to: JList[BoxData[_ <: Proposition, _ <: Box[_ <: Proposition]]] = new JArrayList()

    val creator = PrivateKey25519Creator.getInstance
    val pk1 = creator.generateSecret("test_seed1".getBytes)
    val pk2 = creator.generateSecret("test_seed2".getBytes)
    val pk3 = creator.generateSecret("test_seed3".getBytes)

    from.add(new JPair[ZenBox, PrivateKey25519](getZenBox(pk1.publicImage(), 1, 60), pk1))
    from.add(new JPair[ZenBox, PrivateKey25519](getZenBox(pk2.publicImage(), 1, 50), pk2))
    from.add(new JPair[ZenBox, PrivateKey25519](getZenBox(pk3.publicImage(), 1, 20), pk3))

    val pk4 = creator.generateSecret("test_seed4".getBytes)
    val pk5 = creator.generateSecret("test_seed5".getBytes)
    val pk6 = creator.generateSecret("test_seed6".getBytes)

    to.add(new ZenBoxData(pk4.publicImage, 10L))
    to.add(new ZenBoxData(pk5.publicImage, 20L))
    to.add(new ZenBoxData(pk6.publicImage, 90L))

    val transaction = RegularTransaction.create(from, to, fee)

    val serializer = ApplicationJsonSerializer.getInstance()
    serializer.setDefaultConfiguration()

    val jsonStr = serializer.serialize(transaction)

    val node: JsonNode = serializer.getObjectMapper().readTree(jsonStr)

    try {
      val id = node.path("id").asText()
      assertEquals("Transaction id json value must be the same.",
        ScorexEncoder.default.encode(transaction.id), id)
    } catch {
      case _: Throwable => fail("Transaction id doesn't not found in json.")
    }

    try {
      val fee_parsed = node.path("fee").asLong()
      assertEquals("Transaction fee json value must be the same.",
        transaction.fee(), fee_parsed)
    } catch {
      case _: Throwable => fail("Transaction fee doesn't not found in json.")
    }

    try {
      val inputsNode = node.path("unlockers")
      try {
        assertEquals("Count of transaction unlockers in json must be the same.",
          transaction.unlockers().size(), inputsNode.size)
      } catch {
        case _: Throwable => fail("Transaction unlockers in json have invalid format.")
      }
    } catch {
      case _: Throwable => fail("Transaction unlockers do not found in json.")
    }

    try {
      val newBoxesNode = node.path("newBoxes")
      try {
        assertEquals("Count of transaction new boxes in json must be the same.",
          transaction.newBoxes().size(), newBoxesNode.size)
      } catch {
        case _: Throwable => fail("Transaction newBoxes in json have invalid format.")
      }
    } catch {
      case _: Throwable => fail("Transaction new boxes do not found in json.")
    }

    try {
      val typeName = node.path("typeName").asText()
      try {
        assertTrue("Type name should be the same", typeName.equals("RegularTransaction"))
      } catch {
        case _: Throwable => fail("TypeName have invalid value.")
      }
    } catch {
      case _: Throwable => fail("TypeName is not found in json.")
    }

    try {
      val isCustom = node.path("isCustom").asBoolean()
      try {
        assertTrue("Regular transaction should not be custom.", isCustom)
      } catch {
        case _: Throwable => fail("Field isCustom have invalid value.")
      }
    } catch {
      case _: Throwable => fail("Field isCustom is not found in json.")
    }
  }

}
