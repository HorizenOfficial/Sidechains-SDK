package io.horizen.account.sc2sc

import io.horizen.account.sc2sc.CrossChainRedeemMessageProcessorImpl.receiverSidechain
import io.horizen.utils.BytesUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCrossChainRedeemMessageTest {

  @Test
  def test(): Unit = {
    // Arrange
    val messageType = 1
    val sender = "d504dbfde192182c68d2".getBytes
    val receiver = "0303908afe9d1078bdf1".getBytes
    val payload = "1234".getBytes

    val certificateDataHash = BytesUtils.fromHexString("8b4a3cf70f33a2b9692d1bd5c612e2903297b35289e59c9be7afa0984befd230")
    val nextCertificateDataHash = BytesUtils.fromHexString("1701e3d5c949797c469644a8c7ff495ee28259c5548d7879fcc5518fe1e2163c")
    val scCommitmentTreeRoot = CrossChainRedeemMessageProcessorImpl.scCommitmentTreeRoot
    val nextScCommitmentTreeRoot = CrossChainRedeemMessageProcessorImpl.nextScCommitmentTreeRoot
    val proof = "proof".getBytes
    val accCcRedeemMsg = AccountCrossChainRedeemMessage(
      messageType, sender, receiverSidechain, receiver, payload, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
    )

    // Act
    val encoded = accCcRedeemMsg.encode

    // Assert
    assertTrue(encoded.nonEmpty)
  }
}