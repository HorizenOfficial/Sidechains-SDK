package com.horizen.sc2sc

import java.nio.charset.StandardCharsets
import com.horizen.fixtures.SecretFixture
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertArrayEquals, assertEquals}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

class CrossChainMessageTest extends JUnitSuite with SecretFixture with CrossChainMessageFixture{


  @Test
  def testSerialize(): Unit = {

    val msg = getCrossChainMessage(getAddressProposition(42342), getAddressProposition(6346))
    val serBytes =  msg.serializer.toBytes(msg)
    val reparsed = msg.serializer.parseBytes(serBytes)

    assertEquals("Error in CrossChainMessage serialization", msg.messageType, reparsed.messageType)
    assertEquals("Error in CrossChainMessage serialization", senderSidechainIdHex, BytesUtils.toHexString(reparsed.senderSidechain))
    assertEquals("Error in CrossChainMessage serialization", receiverSidechainIdHex, BytesUtils.toHexString(reparsed.receiverSidechain))
    assertArrayEquals("Error in CrossChainMessage serialization", msg.sender, reparsed.sender)
    assertArrayEquals("Error in CrossChainMessage serialization", msg.receiver, reparsed.receiver)
    assertEquals("Error in CrossChainMessage serialization", payloadString, new String(reparsed.payload, StandardCharsets.UTF_8))


  }
}
