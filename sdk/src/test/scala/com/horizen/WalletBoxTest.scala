package com.horizen

import scorex.core.{NodeViewModifier, bytesToId, idToBytes}

import com.horizen.fixtures.BoxFixture
import com.horizen.utils.BytesUtils
import org.junit._
import org.junit.Assert._
import org.scalatestplus.junit.JUnitSuite

import scala.util.Random


class WalletBoxTest extends JUnitSuite with BoxFixture {

  @Test
  def WalletBox_CreationTest() {
    val box = getZenBox(getPrivateKey25519("seed1".getBytes), 1, 100)
    val transactionId = bytesToId(new Array[Byte](32))
    val creationTime = 10000


    // Test 1: success creation
    val walletBox = new WalletBox(box, transactionId, creationTime)
    assertEquals("WalletBox creation: box is wrong", box, walletBox.box)
    assertEquals("WalletBox creation: transaction ID is wrong", transactionId, walletBox.transactionId)
    assertEquals("WalletBox creation: creation time is wrong", creationTime, walletBox.createdAt)


    // Test 2: transaction id length has invalid length (!= 32)
    var exceptionOccurred = false
    try {
      new WalletBox(box, bytesToId(new Array[Byte](31)), creationTime)
    }
    catch {
      case _ : Throwable => exceptionOccurred = true
    }
    assertTrue("WalletBox creation: exception expected.", exceptionOccurred)


    // Test 2: createdAt value is negative
    exceptionOccurred = false
    try {
      new WalletBox(box, bytesToId(new Array[Byte](32)), -100)
    }
    catch {
      case _ : Throwable => exceptionOccurred = true
    }
    assertTrue("WalletBox creation: exception expected.", exceptionOccurred)
  }

  @Test
  def WalletBox_ComparisonTest(): Unit = {
    val walletBox1 = new WalletBox(
      getZenBox(getPrivateKey25519("seed1".getBytes), 1, 100),
      bytesToId(new Array[Byte](32)),
      10000)
    var walletBox2: WalletBox = null


    // Test 1: compare 2 equals wallet boxes
    walletBox2 = new WalletBox(
      getZenBox(getPrivateKey25519("seed1".getBytes), 1, 100),
      bytesToId(new Array[Byte](32)),
      10000)
    assertEquals("WalletBoxes hash codes expected to be equal", walletBox1.hashCode, walletBox2.hashCode)
    assertEquals("WalletBoxes expected to be equal", walletBox1, walletBox2)


    // Test 2: change box
    walletBox2 = new WalletBox(
      getZenBox(getPrivateKey25519("seed2".getBytes), 2, 200),
      bytesToId(new Array[Byte](32)),
      10000)

    assertNotEquals("WalletBoxes hash codes expected to NOT be equal", walletBox1.hashCode, walletBox2.hashCode)
    assertNotEquals("WalletBoxes expected to be NOT equal", walletBox1, walletBox2)


    // Test 3: change transaction id
    val anotherTransactionIdBytes = new Array[Byte](32)
    Random.nextBytes(anotherTransactionIdBytes)

    walletBox2 = new WalletBox(
      getZenBox(getPrivateKey25519("seed1".getBytes), 1, 100),
      bytesToId(anotherTransactionIdBytes),
      10000)

    assertEquals("WalletBoxes hash codes expected to be equal", walletBox1.hashCode, walletBox2.hashCode)
    assertNotEquals("WalletBoxes expected to be NOT equal", walletBox1, walletBox2)


    // Test 4: change creationTime
    walletBox2 = new WalletBox(
      getZenBox(getPrivateKey25519("seed1".getBytes), 1, 100),
      bytesToId(new Array[Byte](32)),
      20000)
    assertEquals("WalletBoxes hash codes expected to be equal", walletBox1.hashCode, walletBox2.hashCode)
    assertNotEquals("WalletBoxes expected to be NOT equal", walletBox1, walletBox2)
  }
}
