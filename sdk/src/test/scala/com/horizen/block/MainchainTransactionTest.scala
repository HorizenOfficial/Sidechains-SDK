package com.horizen.block

import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Test, Ignore}
import org.scalatest.junit.JUnitSuite
import com.horizen.librustsidechains.{ Utils => ScCryptoUtils }

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

    assertEquals("Tx Hash is different.", "3159d9dfee9963fa0c915057c118df0c9b8e51cad618c9b3730dcdc10acbbf4b", tx.hashHex)
    assertEquals("Tx Size is different.", 296, tx.size)
  }

  @Test
  def tx_vminus4_single_ft(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_single_ft").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "1738af235d86a2fc3d359268de3f059650adbf6e52a840e5457f693f471a47b2"
    val sidechainId: ByteArrayWrapper = new ByteArrayWrapper(BytesUtils.fromHexString(sidechainIdHex))

    val expectedTxHash: String = "4a9bce10b667c9d48d5092b0fd7c252e4d7fb3bbe7c2416aaa69954458cf6f22"
    val expectedTxSize: Int = 450
    val expectedAmount: Long = 1000000000L // 10 Zen
    val expectedPublicKey: String = "a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac"

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '$sidechainIdHex'.", 1, crosschainOutputs.size)
    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxForwardTransferCrosschainOutput])
    assertEquals("Crosschain output sidechain is different.", sidechainId, new ByteArrayWrapper(crosschainOutputs.head.sidechainId))

    val ft: MainchainTxForwardTransferCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer hash is different.","35c6569812125a6dfc0f651954c21c76fd55e9411d77759e320a315623c64abf", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", expectedAmount, ft.amount)
  }

  @Test
  def tx_vminus4_multiple_ft(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_multiple_ft").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "1738af235d86a2fc3d359268de3f059650adbf6e52a840e5457f693f471a47b2"

    val expectedTxHash: String = "7cbdc9068acc0768d588ea6693c926cba76cc810916bb4c2da41cff7d403ae8e"
    val expectedTxSize: Int = 742

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '$sidechainIdHex'.", 3, crosschainOutputs.size)


    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxForwardTransferCrosschainOutput])
    var ft: MainchainTxForwardTransferCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer hash is different.","20b236573f7fb85e6a35746748eaf48b7f94f5a5dfead6a5b0734c49a26bc356", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","000000000000000000000000000000000000000000000000000000000000add1", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1000000000L, ft.amount)

    assertTrue("Crosschain output type is different.", crosschainOutputs(1).isInstanceOf[MainchainTxForwardTransferCrosschainOutput])
    ft = crosschainOutputs(1).asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer hash is different.","b86777cfc0c52f953ef66a9f6eb86ed2b5f5b8ac3ec0e2b6cf3a0f376c8d28ed", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","000000000000000000000000000000000000000000000000000000000000add2", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1100000000L, ft.amount)

    assertTrue("Crosschain output type is different.", crosschainOutputs(2).isInstanceOf[MainchainTxForwardTransferCrosschainOutput])
    ft = crosschainOutputs(2).asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer hash is different.","24dfe10cf57e8559c109c068e7805d2ff0be5c5d3ee704aed15440b65598f676", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","000000000000000000000000000000000000000000000000000000000000add3", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1200000000L, ft.amount)
  }

  @Test
  def tx_vminus4_sc_creation(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_sc_creation").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "396cc1fcd5677b96e7aceb0c82fab7574920af4684e8d60e81dc8f7d6beac7ef"

    val expectedTxHash: String = "a84a505a2f0aaf340caa489ca5830e1c3c1bf382be75fa6cc46d9e4956c3fb5e"
    val expectedTxSize: Int = 1814
    val expectedAmount: Long = 5000000000L // 50 Zen
    val expectedWithdrawalEpochLength: Int = 1000

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '$sidechainIdHex'.", 1, crosschainOutputs.size)


    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxSidechainCreationCrosschainOutput])
    val creation: MainchainTxSidechainCreationCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxSidechainCreationCrosschainOutput]
    assertEquals("Sidechain creation hash is different.","5066d95622d3f7282f427f4c92c85176bc2aaed7590e8de007e7a8cb222a8e11", BytesUtils.toHexString(creation.hash))
    assertEquals("Sidechain creation sc id is different.", sidechainIdHex, BytesUtils.toHexString(creation.sidechainId))
    assertEquals("Sidechain creation withdrawal epoch length is different.", expectedWithdrawalEpochLength, creation.withdrawalEpochLength)
    assertEquals("Sidechain creation amount is different.", expectedAmount, creation.amount)
    assertEquals("Sidechain creation address is different.", "a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac",
      BytesUtils.toHexString(creation.address))
    assertEquals("Sidechain creation custom data is different.", "802b9fca06d7bba9ce270b05737450b4bb54143452e8eae8e7541b3d1f868d8324",
      BytesUtils.toHexString(creation.customCreationData))
  }

  @Test
  def tx_vminus4_single_mbtr(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_mbtr").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "1eb27ec23347fb36c9ddbf6db9a4d48d938dd9072c521f8c335f71788eccc066"

    val expectedTxHash: String = "7d4147bc7acb92982a355c7d4dde375aec8b282da458a26777a1c4f42adb765f"
    val expectedTxSize: Int = 504
    val expectedScFee: Long = 1000000000L // 10 Zen

    val expectedScRequestData = Seq(
      "00565b986a6b3dced1b63cfc6f9c448337a2251a9caad9711dc58aefd5ecbabf",
      "00c1c066ea400f754bce95adb4a454c12fc9608f796016b13de9a60faa4cc396"
    )

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '$sidechainIdHex'.", 1, crosschainOutputs.size)


    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxBwtRequestCrosschainOutput])
    val mbtr: MainchainTxBwtRequestCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxBwtRequestCrosschainOutput]
    assertEquals("MBTR output hash is different.","bb928d79edd7540721f630bb1572fe03e114de84ca031f93ad6cfbc63dee3850", BytesUtils.toHexString(mbtr.hash))
    assertEquals("MBTR output sc id is different.", sidechainIdHex, BytesUtils.toHexString(mbtr.sidechainId))
    assertEquals("MBTR output sc fee is different.", expectedScFee, mbtr.scFee)
    assertEquals("MBTR output mcDestinationAddress is different.", "cde8ae26b34c0555d26d43e6fdba8953f36bb9fb",
      BytesUtils.toHexString(mbtr.mcDestinationAddress))
    assertEquals("BTR output scRequestData size is different.", expectedScRequestData.length, mbtr.scRequestData.length)
    for(i <- expectedScRequestData.indices) {
      assertEquals("Sidechain creation custom data is different.", expectedScRequestData(i),
        BytesUtils.toHexString(mbtr.scRequestData(i)))
    }
  }

  @Test
  def tx_vminus4_csws(): Unit = {
    // Transaction with 4 CSWs
    val hex : String = Source.fromResource("mctx_v-4_csw").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get

    val expectedTxHash: String = "fd1ec5ac2835b9e8862f33e4f09a18e60d9a568a73bb84d63652d58156b3c6fb"
    val expectedTxSize: Int = 11722

    assertEquals("Tx Hash is different.", expectedTxHash, tx.hashHex)
    assertEquals("Tx Size is different.", expectedTxSize, tx.size)
  }

  @Test
  def calculateSidechainIdRegression(): Unit = {
    val tmpHash = new Array[Byte](32)
    val tmpPos = 0

    val sc_id_bytes = ScCryptoUtils.calculateSidechainId(tmpHash, tmpPos)
    assertEquals("Different sc id expected.",
      "e5898923c5501dbecd48456555cf9225aa44bf3a4e84bc20ec069b4a4dcf972a",
      BytesUtils.toHexString(sc_id_bytes))
  }
}
