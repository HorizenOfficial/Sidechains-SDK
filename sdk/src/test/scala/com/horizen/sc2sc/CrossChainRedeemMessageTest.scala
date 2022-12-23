package com.horizen.sc2sc

import com.horizen.fixtures.SecretFixture
import org.junit.Assert.{assertArrayEquals, assertEquals}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import com.horizen.fixtures.FieldElementFixture
import com.horizen.sc2sc.baseprotocol.CrossChainRedeemMessage

class CrossChainRedeemMessageTest extends JUnitSuite with SecretFixture with CrossChainMessageFixture {



  @Test
  def testSerialize(): Unit = {

    val msg = getCrossChainMessage(getAddressProposition(42342), getAddressProposition(6346))
    val certificateDataHash = FieldElementFixture.generateFieldElement()
    val nextCertificateDataHash = FieldElementFixture.generateFieldElement()
    val scCommitmentTreeRoot = FieldElementFixture.generateFieldElement()
    val nextScCommitmentTreeRoot = FieldElementFixture.generateFieldElement()
    val proof = FieldElementFixture.generateFieldElement()

    val redeemMsg = CrossChainRedeemMessage(msg,
      certificateDataHash, nextCertificateDataHash,
      scCommitmentTreeRoot, nextScCommitmentTreeRoot,
      proof)

    var serBytes =  redeemMsg.serializer.toBytes(redeemMsg)
    var reparsed = redeemMsg.serializer.parseBytes(serBytes)

    assertEquals("Error in CrossChainRedeemMessage serialization", redeemMsg.message.messageType, reparsed.message.messageType)
    assertArrayEquals("Error in CrossChainRedeemMessage serialization", redeemMsg.message.payload,  reparsed.message.payload)
    assertArrayEquals("Error in CrossChainRedeemMessage serialization", redeemMsg.proof,  reparsed.proof)

  }
}
