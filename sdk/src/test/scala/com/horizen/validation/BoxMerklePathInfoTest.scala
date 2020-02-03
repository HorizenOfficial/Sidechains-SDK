package com.horizen.validation

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import java.util

import com.horizen.utils.{BoxMerklePathInfo, BoxMerklePathInfoSerializer, BytesUtils, MerklePath, Pair}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import java.util.{ArrayList => JArrayList}
import java.lang.{Byte => JByte}

import org.junit.Assert.{assertEquals, assertNotEquals, assertTrue, fail}

class BoxMerklePathInfoTest extends JUnitSuite {
  val boxIdHex = "abc0000000000000000000000000000000000000000000000000000000000123"
  val boxId: Array[Byte] = BytesUtils.fromHexString(boxIdHex)
  val emptyMerklePath: MerklePath = new MerklePath(new JArrayList())

  val nonEmptyMerklePath: MerklePath = new MerklePath(util.Arrays.asList(
    new Pair[JByte, Array[Byte]](0.toByte, BytesUtils.fromHexString("29d000eee85f08b6482026be2d92d081d6f9418346e6b2e9fe2e9b985f24ed1e")),
    new Pair[JByte, Array[Byte]](1.toByte, BytesUtils.fromHexString("61bfbdf7038dc7f21e2bcf193faef8e6caa8222af016a6ed86b9e9d860f046df"))
  ))

  @Test
  def comparison(): Unit = {
    assertNotEquals("Box merkle path info expected to be different.", emptyMerklePath, nonEmptyMerklePath)
  }

  @Test
  def serialization(): Unit = {
    // Test 1: empty merkle path (single element in merkle tree)
    val boxWithEmptyPath = BoxMerklePathInfo(boxId, emptyMerklePath)
    var boxBytes = boxWithEmptyPath.bytes
    var deserializedBox = BoxMerklePathInfoSerializer.parseBytes(boxBytes)
    assertEquals("Deserialized box merkle path info expected to be equal to the original one.", boxWithEmptyPath, deserializedBox)


    // Test 2: non empty merkle path
    val boxWithNonEmptyPath = BoxMerklePathInfo(boxId, nonEmptyMerklePath)
    boxBytes = boxWithNonEmptyPath.bytes
    deserializedBox = BoxMerklePathInfoSerializer.parseBytes(boxBytes)
    assertEquals("Deserialized box merkle path info expected to be equal to the original one.", boxWithNonEmptyPath, deserializedBox)

    // Uncomment and run if you want to update regression data.
    // val out = new BufferedWriter(new FileWriter("src/test/resources/boxmerklepathinfo_hex"))
    //out.write(BytesUtils.toHexString(boxBytes))
    // out.close()


    // Test 3: try to deserialize broken bytes.
    assertTrue("BoxMerklePathInfo expected to be not parsed due to broken data.", BoxMerklePathInfoSerializer.parseBytesTry("broken bytes".getBytes).isFailure)
  }

  @Test
  def serializationRegression(): Unit = {
    var bytes: Array[Byte] = null
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("boxmerklepathinfo_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        fail(e.toString)
    }

    val boxMerklePathInfoTry = BoxMerklePathInfoSerializer.parseBytesTry(bytes)
    assertTrue("BoxMerklePathInfo expected to by parsed.", boxMerklePathInfoTry.isSuccess)

    val boxWithNonEmptyPath = BoxMerklePathInfo(boxId, nonEmptyMerklePath)
    assertEquals("Parsed info is different to original.", boxWithNonEmptyPath, boxMerklePathInfoTry.get)
  }
}
