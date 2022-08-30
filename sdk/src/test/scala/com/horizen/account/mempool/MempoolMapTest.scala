package com.horizen.account.mempool

import com.horizen.account.fixtures.EthereumTransactionFixture
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import com.horizen.SidechainTypes

import java.math.BigInteger

class MempoolMapTest
  extends JUnitSuite
    with EthereumTransactionFixture
    with SidechainTypes
    with MockitoSugar
{

  @Before
  def setUp() : Unit = {
  }

  @Test
  def testAddTx(): Unit = {
    val mempoolMap = MempoolMap()
    val initialStateNonce = BigInteger.ZERO
    val value = BigInteger.TEN

    val executableTransaction0 = createEIP1559Transaction(value, initialStateNonce)
    assertFalse("Empty Mempool contains tx", mempoolMap.contains(executableTransaction0))
    assertFalse("Empty Mempool contains tx account info", mempoolMap.containsAccountInfo(executableTransaction0.getFrom))

    assertTrue("It should not be possible adding a tx to a not initialized mempoolMap", mempoolMap.add(executableTransaction0).isFailure)

    mempoolMap.initializeAccount(initialStateNonce, executableTransaction0.getFrom)

    assertFalse("Empty Mempool contains tx", mempoolMap.contains(executableTransaction0))
    assertTrue("Initialized Mempool doesn't contain tx account info", mempoolMap.containsAccountInfo(executableTransaction0.getFrom))

    var res = mempoolMap.add(executableTransaction0)
    assertTrue("Adding transaction failed", res.isSuccess)
    assertTrue("Mempool doesn't contain transaction", res.get.contains(executableTransaction0))

    assertEquals("Added transaction is not executable", executableTransaction0.id(), res.get.executableTxs.get(executableTransaction0.getFrom).get.get(executableTransaction0.getNonce).get)

    res = mempoolMap.add(executableTransaction0)
    assertTrue("Adding twice the same tx should fail", res.isFailure)

    val executableTransaction1 = createEIP1559Transaction(value, initialStateNonce.add(BigInteger.ONE))

  }

//  @Test
//  def testIsValidZenAmount(): Unit = {
//    intercept[IllegalArgumentException] {
//      ZenWeiConverter.isValidZenAmount(null)
//     }
//
//    val negativeZenAmount = BigInteger.valueOf(-10L)
//    assertFalse("A negative value is a valid Zen amount", ZenWeiConverter.isValidZenAmount(negativeZenAmount))
//
//    val tooBigZenAmount = BigInteger.valueOf(21000001L).multiply(BigInteger.valueOf(100000000)).multiply(BigInteger.valueOf(10000000000L))//21+ million zen to zennies to wei
//    assertFalse("A zen value bigger than max amount of zen is a valid Zen amount", ZenWeiConverter.isValidZenAmount(tooBigZenAmount))
//
//    val notAMultipleOfZennyAmount = BigInteger.valueOf(11000000000L)//1.1 zenny in wei
//    assertFalse("A wei value that represents a fractions of zenny is a valid Zen amount", ZenWeiConverter.isValidZenAmount(notAMultipleOfZennyAmount))
//  }
//
//  @Test
//  def testConvertWeiToZennies(): Unit = {
//    intercept[IllegalArgumentException] {
//      ZenWeiConverter.convertWeiToZennies(null)
//      val notAMultipleOfZennyAmount = BigInteger.valueOf(11000000000L)//1.1 zenny in wei
//      ZenWeiConverter.convertWeiToZennies(notAMultipleOfZennyAmount)
//    }
//
//    val expectedZenAmount = 10000000
//    val zenAmountInWei = BigInteger.valueOf(expectedZenAmount).multiply(BigInteger.valueOf(10000000000L))//0.1 zen to zennies to wei
//    assertEquals("Wrong zen amount", expectedZenAmount, ZenWeiConverter.convertWeiToZennies(zenAmountInWei))
//
//  }


}
