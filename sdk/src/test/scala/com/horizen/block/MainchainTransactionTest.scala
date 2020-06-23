package com.horizen.block

import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Test, Ignore}
import org.scalatest.junit.JUnitSuite

import scala.io.Source

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

  // Fix test
  @Test
  def tx_vminus4_single_ft(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_single_ft").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "8e87cf97de8f5f565c5cffeebb8f3db1abc6386bd6cb7143bf2e7c753587b023"
    val sidechainId: ByteArrayWrapper = new ByteArrayWrapper(BytesUtils.fromHexString(sidechainIdHex))


    assertEquals("Tx Hash is different.", "a42c8e82062639cc96e95ffd1bce7c7f7dc0440360b0a21034c9008c0d98cd76", tx.hashHex)
    assertEquals("Tx Size is different.", 300, tx.size)
    assertEquals("Tx contains different number of mentioned sidechains.", 1, tx.getRelatedSidechains.size)
    assertTrue(s"SidechainId '${sidechainIdHex}' is missed.",
      tx.getRelatedSidechains.contains(sidechainId))

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs(sidechainId)
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '${sidechainIdHex}'.", 1, crosschainOutputs.size)
    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxForwardTransferCrosschainOutput])

    val ft: MainchainTxForwardTransferCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxForwardTransferCrosschainOutput]
    assertEquals("Forward Transfer type is different.", MainchainTxForwardTransferCrosschainOutput.OUTPUT_TYPE, ft.outputType)
    assertEquals("Forward Transfer hash is different.","1e8e9a9c1d8e18cb978b0e29fc90cfbb617fc6326741f3fa34b8cec6a1af846b", BytesUtils.toHexString(ft.hash))
    assertEquals("Forward Transfer sc id is different.", sidechainIdHex, BytesUtils.toHexString(ft.sidechainId))
    assertEquals("Forward Transfer proposition is different.","a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac", BytesUtils.toHexString(ft.propositionBytes))
    assertEquals("Forward Transfer amount is different.", 1000000000, ft.amount)
  }

  @Test
  def tx_vminus4_sc_creation(): Unit = {
    val hex : String = Source.fromResource("mctx_v-4_sc_creation").getLines().next()
    val bytes: Array[Byte] = BytesUtils.fromHexString(hex)

    val tx: MainchainTransaction = MainchainTransaction.create(bytes, 0).get
    val sidechainIdHex: String = "efad97d0a9ae9d846d11b7f93ba1f4329f8d4cd06708f054f880fbf7d78d9276"
    val sidechainId: ByteArrayWrapper = new ByteArrayWrapper(BytesUtils.fromHexString(sidechainIdHex))


    assertEquals("Tx Hash is different.", "b202c7d52364b4a4ff2ab8dbf64d12eb88aba7f07f869551d3be74797f977286", tx.hashHex)
    assertEquals("Tx Size is different.", 3288, tx.size)
    assertEquals("Tx contains different number of mentioned sidechains.", 1, tx.getRelatedSidechains.size)
    assertTrue(s"SidechainId '${sidechainIdHex}' is missed.",
      tx.getRelatedSidechains.contains(sidechainId))

    val crosschainOutputs: Seq[MainchainTxCrosschainOutput] = tx.getCrosschainOutputs(sidechainId)
    assertEquals(s"Tx expected to have different number of crosschain outputs related to sidechainId '${sidechainIdHex}'.", 1, crosschainOutputs.size)


    assertTrue("Crosschain output type is different.", crosschainOutputs.head.isInstanceOf[MainchainTxSidechainCreationCrosschainOutput])
    val creation: MainchainTxSidechainCreationCrosschainOutput = crosschainOutputs.head.asInstanceOf[MainchainTxSidechainCreationCrosschainOutput]
    assertEquals("Sidechain creation type is different.", MainchainTxSidechainCreationCrosschainOutput.OUTPUT_TYPE, creation.outputType)
    assertEquals("Sidechain creation hash is different.","3bb19e355f7d1bb4c7afadbf459b5a7cefef43afedd220eb072002d13fe8c3d0", BytesUtils.toHexString(creation.hash))
    assertEquals("Sidechain creation sc id is different.", sidechainIdHex, BytesUtils.toHexString(creation.sidechainId))
    assertEquals("Sidechain creation withdrawal epoch length is different.", 10, creation.withdrawalEpochLength)
    assertEquals("Sidechain creation amount is different.", 10000000000L, creation.amount)
    assertEquals("Sidechain creation address is different.", "a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac",
      BytesUtils.toHexString(creation.address))
    assertEquals("Sidechain creation custom data is different.", "dd2de641154fd54de4cf60ea3f5b9e7135787ecb9fcce75de5c41f974fd0cbf70af51ba99b1b8d591d237091414051d2953b7d75e16d89be6fe1cf0bfc63a244f6f51159061875ff1922c3d923d365370ac2605c19e03d674bf64af9e91e00003a6fe5d3f1bcddf09faee1866e453f99d4491e68811bc1a7d5695955e4f8f456627f546bdbbbd026c1b6ee35e2f65659cbcd32406026ebb8f602c86d3f42499f8412dc3ebe664ce188c69360f13dddbd577513171f49423d51ff9578b159010000",
      BytesUtils.toHexString(creation.customData))
  }
}
