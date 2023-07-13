package io.horizen.utxo.wallet

import io.horizen.SidechainTypes
import io.horizen.utxo.box.BoxSerializer
import io.horizen.utxo.companion.SidechainBoxesCompanion
import io.horizen.utxo.customtypes.{CustomBox, CustomBoxSerializer}
import io.horizen.utxo.fixtures.BoxFixture
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.core.bytesToId

import java.lang.{Byte => JByte}
import java.nio.charset.StandardCharsets
import java.util.{HashMap => JHashMap}
import scala.util.Random

class WalletBoxSerializerTest extends JUnitSuite with BoxFixture {
  @Test
  def WalletBoxSerializerTest_SerializationTest(): Unit = {
    val transactionIdBytes = new Array[Byte](32)
    var customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
    customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
    val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers, false)

    var serializer: WalletBoxSerializer = null
    var bytes: Array[Byte] = null

    // Test 1: serialization for core Box
    Random.nextBytes(transactionIdBytes)
    val walletBoxWithZenBox = new WalletBox(
      getZenBox(getPrivateKey25519("seed1".getBytes(StandardCharsets.UTF_8)), 1, 100),
      bytesToId(transactionIdBytes),
      10000)
    serializer = walletBoxWithZenBox.serializer(sidechainBoxesCompanion)
    bytes = serializer.toBytes(walletBoxWithZenBox)
    val parsedWalletBoxWithZenBox = serializer.parseBytesTry(bytes).get
    assertEquals("Core WalletBoxes expected to be equal.", walletBoxWithZenBox, parsedWalletBoxWithZenBox)


    // Test 2: serialization of custom Box
    Random.nextBytes(transactionIdBytes)
    val walletBoxWithCustomBox = new WalletBox(
      getCustomBox.asInstanceOf[SidechainTypes#SCB],
      bytesToId(transactionIdBytes),
      20000)
    serializer = walletBoxWithCustomBox.serializer(sidechainBoxesCompanion)
    bytes = serializer.toBytes(walletBoxWithCustomBox)
    val parsedWalletBoxWithCustomBox = serializer.parseBytesTry(bytes).get
    assertEquals("Custom WalletBoxes expected to be equal.", walletBoxWithCustomBox, parsedWalletBoxWithCustomBox)


    // Test 3: try parse of broken bytes
    val failureExpected: Boolean = new WalletBoxSerializer(sidechainBoxesCompanion).parseBytesTry("broken bytes".getBytes(StandardCharsets.UTF_8)).isFailure
    assertEquals("Failure during parsing expected.", true, failureExpected)

  }
}
