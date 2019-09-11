package com.horizen.block

import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.io.Source
import scala.util.Try

// tx data in RPC byte order: https://explorer.zen-solutions.io/api/rawtx/<tx_id>
class MainchainTransactionTest extends JUnitSuite {

  @Test
  def MainchainTransactionTest_tx_v1_coinbase(): Unit = {
    val hex : String = Source.fromResource("mctx_v1_coinbase").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "e2f681e0431bc5f77299373632350ac493211fa8f3d0491a2d6c5e0284f5d377", tx.hashHex)
    assertEquals("Tx Size is different.", 190, tx.size)
  }

  @Test
  def MainchainTransactionTest_tx_v1(): Unit = {
    val hex : String = Source.fromResource("mctx_v1").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "6054f092033e5bb352d46ddb837b10da91eb43b40da46656e46140e3ce938db9", tx.hashHex)
    assertEquals("Tx Size is different.", 9150, tx.size)
  }

  @Test
  def MainchainTransactionTest_tx_v2(): Unit = {
    val hex : String = Source.fromResource("mctx_v2").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "3c1bd6d388a731016fbc809ab032b35356e64b2e7601ede00fd430fb612937ac", tx.hashHex)
    assertEquals("Tx Size is different.", 1909, tx.size)
  }

  @Test
  def MainchainTransactionTest_tx_vminus3(): Unit = {
    val hex : String = Source.fromResource("mctx_v-3").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    assertEquals("Tx Hash is different.", "dee5a3758cee29648a6a50edf26c56db60c1186e434302299fd0f3e8339bf45a", tx.hashHex)
    assertEquals("Tx Size is different.", 1953, tx.size)
  }

  @Test
  def MainchainTransactionTest_tx_vminus4_sc_creation(): Unit = {
    /*val data: String = "fcffffff012124a5a06c058f922a65c5a3a73526b8c682d86be55170723c19f709e99fffe0000000006b483045022100db11174dcbbfb9311bfedcb73b42db7d94f243d3858667353f4fee08bc851e6202202efa97420b82b8739c04f5bc640f3ab8cdc50587e3b3a81e352148d3c08473e501210267cac27ac0f699fb7a77e3f72ee2f91796c9344ef9ed9b7bcad73946f824d05bfeffffff0222d3a23a000000003c76a914e48a635ea8e4f0c387b25087cf96de0d522a204088ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b400e1f505000000003aa914b6863b182a52745bf6d5fb190139a2aa876c08f58720bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b40101000000000000000000000000000000000000000000000000000000000000006400000000038096980000000000d1ad0000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000002d310100000000d2ad000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000080c3c90100000000d3ad0000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000064000000"
    import java.io._
    val pw = new PrintWriter(new File("src/test/resources/mctx_v-4_sc_creation"))
    pw.write(data)
    pw.close*/

    val hex : String = Source.fromResource("mctx_v-4_sc_creation").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "0000000000000000000000000000000000000000000000000000000000000002"
    val sidechainId: ByteArrayWrapper = new ByteArrayWrapper(BytesUtils.fromHexString(sidechainIdHex))


    assertEquals("Tx Hash is different.", "a94c3ccab35f75ce96ce979f95492678a857d69307e9d7079c2068ee0b41faff", tx.hashHex)
    assertEquals("Tx Size is different.", 302, tx.size)
    assertEquals("Tx contains different number of mentioned sidechains.", 1, tx.getRelatedSidechains.size)
    assertTrue(s"SidechainId '${sidechainIdHex}' is missed.",
      tx.getRelatedSidechains.contains(sidechainId))

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs(sidechainId)
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '${sidechainIdHex}'.", 1, crosschainOutputs.size)
    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxForwardTransferCrosschainOutput])

    val ft: MainchainTxForwardTransferCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer type is different.", MainchainTxForwardTransferCrosschainOutput.OUTPUT_TYPE, ft.outputType)
    assertEquals("Forward Transfer hash is different.","8f57131e8c27f8ea6bc8fb28a025c9107569d44541b95371cc36f24a612f44ec", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","000000000000000000000000000000000000000000000000000000000002add3", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1000000, ft.amount)

  }

}
