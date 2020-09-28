package com.horizen.block

import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Test, Ignore}
import org.scalatest.junit.JUnitSuite

import scala.io.Source

// tx data in RPC byte order: https://explorer.zen-solutions.io/api/rawtx/<tx_id>
class MainchainTransactionTest extends JUnitSuite {

  @Test
  def tx_v1_coinbase(): Unit = {
    val hex : String = Source.fromResource("mctx_v1_coinbase").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "e2f681e0431bc5f77299373632350ac493211fa8f3d0491a2d6c5e0284f5d377", tx.hashHex)
    assertEquals("Tx Size is different.", 190, tx.size)
  }

  @Test
  def tx_v1(): Unit = {
    val hex : String = Source.fromResource("mctx_v1").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "6054f092033e5bb352d46ddb837b10da91eb43b40da46656e46140e3ce938db9", tx.hashHex)
    assertEquals("Tx Size is different.", 9150, tx.size)
  }

  @Test
  def tx_v2(): Unit = {
    val hex : String = Source.fromResource("mctx_v2").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "3c1bd6d388a731016fbc809ab032b35356e64b2e7601ede00fd430fb612937ac", tx.hashHex)
    assertEquals("Tx Size is different.", 1909, tx.size)
  }

  @Test
  def tx_vminus3(): Unit = {
    val hex : String = Source.fromResource("mctx_v-3").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "dee5a3758cee29648a6a50edf26c56db60c1186e434302299fd0f3e8339bf45a", tx.hashHex)
    assertEquals("Tx Size is different.", 1953, tx.size)
  }

  @Test
  def tx_vminus4_without_sc_data(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_without_sc_data").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "b20a27bf54eb7bded0d4680994a89ab4e7001ceaef4b66b7bbdcbe76346d01fb", tx.hashHex)
    assertEquals("Tx Size is different.", 295, tx.size)
  }

  @Test
  def tx_vminus4_single_ft(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_single_ft").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "16bec8b8beaa2bfc6ca7f3e5329542d96819db35eeec6bf54294f28407b9e955"
    val sidechainId: ByteArrayWrapper = new ByteArrayWrapper(BytesUtils.fromHexString(sidechainIdHex))

    val expectedTxHash: String = "6e89c8e44ac1d43f33e95308704f4703436fb218a7e750b9c3c10e375cef7555"
    val expectedTxSize: Int = 447
    val expectedAmount: Long = 1000000000L // 10 Zen
    val expectedPublicKey: String = "a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac"

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)
    assertEquals("Tx contains different number of mentioned sidechains.", 1, tx.getRelatedSidechains.size)
    assertTrue(s"SidechainId '$sidechainIdHex' is missed.",
      tx.getRelatedSidechains.contains(sidechainId))

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs(sidechainId)
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '$sidechainIdHex'.", 1, crosschainOutputs.size)
    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxForwardTransferCrosschainOutput])

    val ft: MainchainTxForwardTransferCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer type is different.", MainchainTxForwardTransferCrosschainOutput.OUTPUT_TYPE, ft.outputType)
    assertEquals("Forward Transfer hash is different.","eff765f72dfbf720fb9c81705d5ed856f6d090bf4d65651ecec8479c2298b402", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", expectedAmount, ft.amount)
  }

  @Test
  def tx_vminus4_multiple_ft(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_multiple_ft").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "7aebf04be3c093d2269459dc594b6d39c74db2519cae717a42c1ae6bfb28971a"
    val sidechainId: ByteArrayWrapper = new ByteArrayWrapper(BytesUtils.fromHexString(sidechainIdHex))

    val expectedTxHash: String = "e62169f1e0100ba739bf94a38439f530dbf43df18f834fd9353343108e60c2a2"
    val expectedTxSize: Int = 739

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)
    assertEquals("Tx contains different number of mentioned sidechains.", 1, tx.getRelatedSidechains.size)
    assertTrue(s"SidechainId '$sidechainIdHex' is missed.", tx.getRelatedSidechains.contains(sidechainId))

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs(sidechainId)
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '$sidechainIdHex'.", 3, crosschainOutputs.size)


    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxForwardTransferCrosschainOutput])
    var ft: MainchainTxForwardTransferCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer type is different.", MainchainTxForwardTransferCrosschainOutput.OUTPUT_TYPE, ft.outputType)
    assertEquals("Forward Transfer hash is different.","d2b6e0359c795825ab9ec1ee72487da81c7e66a8553ae4ea67e6dbcfedc70fe7", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","000000000000000000000000000000000000000000000000000000000000add1", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1000000000L, ft.amount)

    assertTrue("Crosschain output type is different.", crosschainOutputs(1).isInstanceOf[MainchainTxForwardTransferCrosschainOutput])
    ft = crosschainOutputs(1).asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer type is different.", MainchainTxForwardTransferCrosschainOutput.OUTPUT_TYPE, ft.outputType)
    assertEquals("Forward Transfer hash is different.","602783a66caf90b30ad864f32b6ca28b55b51c0b5356b5582b709f38354ea7fe", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","000000000000000000000000000000000000000000000000000000000000add2", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1100000000L, ft.amount)

    assertTrue("Crosschain output type is different.", crosschainOutputs(2).isInstanceOf[MainchainTxForwardTransferCrosschainOutput])
    ft = crosschainOutputs(2).asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer type is different.", MainchainTxForwardTransferCrosschainOutput.OUTPUT_TYPE, ft.outputType)
    assertEquals("Forward Transfer hash is different.","7574bd15af83a6927295a73992e13b42b233f3d417d7f91bfc8fc13a1a1aa542", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","000000000000000000000000000000000000000000000000000000000000add3", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1200000000L, ft.amount)
  }

  @Test
  def tx_vminus4_sc_creation(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_sc_creation").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "053c9d27845378108eed154e262cbb63843fd3cb3bf3ca76f159acd185617976"
    val sidechainId: ByteArrayWrapper = new ByteArrayWrapper(BytesUtils.fromHexString(sidechainIdHex))

    val expectedTxHash: String = "3595e7cdcd45fa8c335b7cd9d71ce8d8324a1aa0ba6f298ce328e304975e8bf8"
    val expectedTxSize: Int = 2696
    val expectedAmount: Long = 5000000000L // 50 Zen
    val expectedWithdrawalEpochLength: Int = 1000

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)
    assertEquals("Tx contains different number of mentioned sidechains.", 1, tx.getRelatedSidechains.size)
    assertTrue(s"SidechainId '$sidechainIdHex' is missed.",
      tx.getRelatedSidechains.contains(sidechainId))

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs(sidechainId)
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '$sidechainIdHex'.", 1, crosschainOutputs.size)


    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxSidechainCreationCrosschainOutput])
    val creation: MainchainTxSidechainCreationCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxSidechainCreationCrosschainOutput]
    assertEquals("Sidechain creation type is different.", MainchainTxSidechainCreationCrosschainOutput.OUTPUT_TYPE, creation.outputType)
    assertEquals("Sidechain creation hash is different.","8c77365c2f72d34a17e18050c28716a5a8951767cb0c2d81032690c7eaca6731", BytesUtils.toHexString(creation.hash))
    assertEquals("Sidechain creation sc id is different.", sidechainIdHex, BytesUtils.toHexString(creation.sidechainId))
    assertEquals("Sidechain creation withdrawal epoch length is different.", expectedWithdrawalEpochLength, creation.withdrawalEpochLength)
    assertEquals("Sidechain creation amount is different.", expectedAmount, creation.amount)
    assertEquals("Sidechain creation address is different.", "a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac",
      BytesUtils.toHexString(creation.address))
    assertEquals("Sidechain creation custom data is different.", "2df6c69c3b5d7636512eea9241ebaf0138179ed2b4a3a9d3e06c24d99d5c04712ff31a8cd55bd4b347c39081143031087b28d4c46f31e07eda35256699697e94fcdc95c99855231a0bc499d5e760de37a682a728e34e5ab07a8fdac4b2120100e3eb23a3a2222cc636896601b94697cda1cf6e4df9dfe74db44c90f7d15d1b722f93dd6c754e3bddf80baa2961503ec64c4b3979e558fe3293f88d3031cfd079b325a6db009ca77088aaa55f237e517b4579d2bacf99fade605eab96f636010000",
      BytesUtils.toHexString(creation.customData))
  }
}
