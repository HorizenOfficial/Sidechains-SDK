package com.horizen.account.receipt


import com.horizen.evm.TrieHasher
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.utils.Numeric
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import java.util
import java.util.Map.entry
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ListBuffer
import scala.util.Random


class EthereumReceiptTest
  extends JUnitSuite
    with MockitoSugar
    with ReceiptFixture
{

  // test vectors encoded by go lib have been produced via:
  //     libevm/lib/service_hash_test.go



  @Test
  def receiptSimpleSerDeser(): Unit = {
    val receipt: EthereumReceipt = createTestEthereumReceipt(ReceiptTxType.DynamicFeeTxType.id)
    val r1: String = receipt.toString
    //println(r1)

    val serializedBytes: Array[Byte] = EthereumReceiptSerializer.toBytes(receipt)
    println(BytesUtils.toHexString(serializedBytes))

    val decodedReceipt: EthereumReceipt = EthereumReceiptSerializer.parseBytes(serializedBytes)
    val r2: String = decodedReceipt.toString
    //println(r2)

    assertEquals(r1, r2)
    assertEquals(receipt, decodedReceipt)
    assertEquals(receipt.hashCode(), decodedReceipt.hashCode())
  }

  @Test
  def receiptTestDeriveNonConsensusDataLogs() : Unit = {
    val NUM_LOGS = 2
    val consensusDataReceipt = createTestEthereumConsensusDataReceipt(ReceiptTxType.DynamicFeeTxType.id, NUM_LOGS)
    val txHash = new Array[Byte](32)
    Random.nextBytes(txHash)

    val ethereumReceipt = EthereumReceipt(consensusDataReceipt, txHash, transactionIndex = 1,
      Keccak256.hash("blockhash".getBytes).asInstanceOf[Array[Byte]], 22, BigInteger.valueOf(1234567), BytesUtils.fromHexString("1122334455667788990011223344556677889900"))
    // println(receipt1)

    // we have just consensus data in logs
    val logs = ethereumReceipt.consensusDataReceipt.logs
    assertEquals(logs.size, 2)

    val fullLogs = ethereumReceipt.deriveFullLogs()
    //println(receipt2)

    // after deriving logs we have log index and the same non consensus data as the parent receipt
    assertEquals(fullLogs.size, logs.size)
    assertEquals(BytesUtils.toHexString(fullLogs(0).consensusDataLog.address.toBytes), BytesUtils.toHexString(logs(0).address.toBytes))
    assertEquals(BytesUtils.toHexString(fullLogs(1).consensusDataLog.address.toBytes), BytesUtils.toHexString(logs(1).address.toBytes))

    assertEquals(fullLogs(0).logIndex, 0)
    assertEquals(fullLogs(1).logIndex, 1)

    assertEquals(BytesUtils.toHexString(fullLogs(0).transactionHash), BytesUtils.toHexString(ethereumReceipt.transactionHash))
    assertEquals(BytesUtils.toHexString(fullLogs(1).transactionHash), BytesUtils.toHexString(ethereumReceipt.transactionHash))

    assertEquals(fullLogs(0).blockNumber, ethereumReceipt.blockNumber)
    assertEquals(fullLogs(1).blockNumber, ethereumReceipt.blockNumber)

  }

  @Test def receiptSimpleEncodeDecodeType0Test(): Unit = {
    val receipt = createTestEthereumReceipt(ReceiptTxType.LegacyTxType.id)
    val encodedReceipt = EthereumConsensusDataReceipt.rlpEncode(receipt.consensusDataReceipt)
    //println(BytesUtils.toHexString(encodedReceipt))
    // read what you write
    val decodedReceipt = EthereumConsensusDataReceipt.rlpDecode(encodedReceipt)
    //println(decodedReceipt)
    assertEquals(receipt.consensusDataReceipt, decodedReceipt)
    assertEquals(receipt.consensusDataReceipt.hashCode, decodedReceipt.hashCode)
  }


  @Test def receiptSimpleEncodeDecodeType1Test(): Unit = {
    val receipt = createTestEthereumReceipt(ReceiptTxType.AccessListTxType.id)
    val encodedReceipt = EthereumConsensusDataReceipt.rlpEncode(receipt.consensusDataReceipt)
    //println(BytesUtils.toHexString(encodedReceipt))
    // read what you write
    val decodedReceipt = EthereumConsensusDataReceipt.rlpDecode(encodedReceipt)
    //println(decodedReceipt)
    assertEquals(receipt.consensusDataReceipt, decodedReceipt)
    assertEquals(receipt.consensusDataReceipt.hashCode, decodedReceipt.hashCode)
  }

  @Test def receiptSimpleEncodeDecodeType2Test(): Unit = {
    val receipt = createTestEthereumReceipt(ReceiptTxType.DynamicFeeTxType.id)
    val encodedReceipt = EthereumConsensusDataReceipt.rlpEncode(receipt.consensusDataReceipt)
    //println(BytesUtils.toHexString(encodedReceipt))
    // read what you write
    val decodedReceipt = EthereumConsensusDataReceipt.rlpDecode(encodedReceipt)
    //println(decodedReceipt)
    assertEquals(receipt.consensusDataReceipt, decodedReceipt)
    assertEquals(receipt.consensusDataReceipt.hashCode, decodedReceipt.hashCode)
  }

  @Test def receiptDecodeGoEncodedType0NoLogsTest(): Unit = {
    val dataStrType0 = "f9010801820bb8b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0"
    val dataBytes = BytesUtils.fromHexString(dataStrType0)
    val decodedReceipt = EthereumConsensusDataReceipt.rlpDecode(dataBytes)
    //println(decodedReceipt)
    assertEquals(decodedReceipt.getTxType, ReceiptTxType.LegacyTxType)
    // encode and check we are the same as the original
    val encodedReceipt = EthereumConsensusDataReceipt.rlpEncode(decodedReceipt)
    //println(BytesUtils.toHexString(encodedReceipt))
    assertEquals(BytesUtils.toHexString(encodedReceipt), dataStrType0)
  }

  @Test def receiptDecodeGoEncodedType1NoLogsTest(): Unit = {
    val dataStrType1 = "01f90108018203e8b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0"
    val dataBytes = BytesUtils.fromHexString(dataStrType1)
    val decodedReceipt = EthereumConsensusDataReceipt.rlpDecode(dataBytes)
    //println(decodedReceipt)
    assertEquals(decodedReceipt.getTxType, ReceiptTxType.AccessListTxType)
    // encode and check we are the same as the original
    val encodedReceipt = EthereumConsensusDataReceipt.rlpEncode(decodedReceipt)
    //println(BytesUtils.toHexString(encodedReceipt))
    assertEquals(BytesUtils.toHexString(encodedReceipt), dataStrType1)
  }

  @Test def receiptDecodeGoEncodedType2NoLogsTest(): Unit = {
    val dataStrType2 = "02f9010901830b90f0b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c0"
    val dataBytes = BytesUtils.fromHexString(dataStrType2)
    val decodedReceipt = EthereumConsensusDataReceipt.rlpDecode(dataBytes)
    //println(decodedReceipt)
    assertEquals(decodedReceipt.getTxType, ReceiptTxType.DynamicFeeTxType)
    // encode and check we are the same as the original
    val encodedReceipt = EthereumConsensusDataReceipt.rlpEncode(decodedReceipt)
    //println(BytesUtils.toHexString(encodedReceipt))
    assertEquals(BytesUtils.toHexString(encodedReceipt), dataStrType2)
  }

  // compatible with analogous go code in libevm/lib/service_hash_test.go
  private def generateReceipts(count: Int) : scala.Seq[EthereumConsensusDataReceipt] = {
    val receipts = new Array[EthereumConsensusDataReceipt](count)
    for (i <- receipts.indices) {
      var status = 1
      if (i % 7 == 0) { // mark a number of receipts as failed
        status = 0
      }
      val txType = i % 3
      val cumGas = BigInteger.valueOf(i).multiply(BigInteger.TEN.pow(3))
      //println("cumGas =" + cumGas.toString())
      val logsBloom = new Array[Byte](256)
      val logs = new ListBuffer[EvmLog]
      receipts(i) = new EthereumConsensusDataReceipt(txType, status, cumGas, logs, logsBloom)
      //println("i=" + i + receipts[i].toString())
    }
    receipts.toSeq
  }

  @Test def ethereumReceiptRootHashTest(): Unit = {
    // source of these hashes: TestHashRoot() at libevm/lib/service_hash_test.go
    val testCases : util.Map[Int, String] = util.Map.ofEntries(
      entry(0, "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"),
      entry(1, "0x703c035562d8e37c66f2f9461219b45c710e59c8d0d234f6b062107de627758c"),
      entry(10, "0xfebc2ff67381b064a8b194c9e6a3e771e0daf4a92f31e02946d01ae36bf1122a"),
      entry(1000, "0xf76017831e585d894a579497a9b4054379bef06fedbe2e3e11fed842c57b7d72"),
      entry(126, "0x4cd93fc943ce3de24a4c60ca7b31065ea8a52e21832e454d6adae4b2ac2acc19"),
      entry(127, "0xc65b5ec3b081e44a8ba7da8163749539643d5058b7a2e2cfd628e401b5863756"),
      entry(128, "0x6aa1c5f94dedae352ae4c983eac84921b6b4f66b8f522483b54025b86bfd40c1"),
      entry(129, "0xbd9a3d3db6252d968940aa62a3bb856f8537b775eb675566be49eeafc4cfd0fc"),
      entry(130, "0x62008462d610bf97e84aa1bc8d8b496777530d1cbf876e627ebab7f3a455d857"),
      entry(2, "0x012b665bb84f73c46a83f40969b229a5cbd33f964c0fc3fb6b8114371df42f30"),
      entry(3, "0x34f2a7f429e9a46e8b5b2dfcb9475a83d9fe3a320726e32ed47a1b97c3d9dc86"),
      entry(4, "0x64f4f73bbc52fe58bdfb41f444a285c46db65ebfd882f834851070379abf7c37"),
      entry(51, "0xf0f45d15b0f211d0e10d0b5b0ac0fdcb50d66a7d6b5413f435dcf89581aaaccb"),
      entry(765, "0x204abb0c3e6d4f24a3a6f7c4f90b7185ab618426d05396714d7489c02cd9d7b5")
    )
    for (testCase <- testCases.entrySet.asScala) {
      val receipts = generateReceipts(testCase.getKey)
      val rlpReceipts = receipts.map(r => EthereumConsensusDataReceipt.rlpEncode(r)).toList
      val actualHash = Numeric.toHexString(TrieHasher.Root(rlpReceipts.toArray))
      /*
        println("i: " + testCase.getKey() +
                ", value: " + testCase.getValue().toString() +
                ", actual: "+ actualHash.toString())
       */
      assertEquals("should match transaction root hash", testCase.getValue, actualHash)
    }
  }
}

