package io.horizen.account.utils

import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger

class ZenWeiConverterTest
  extends JUnitSuite
    with MockitoSugar
{

  @Before
  def setUp() : Unit = {
  }


  @Test
  def testIsValidZenAmount(): Unit = {
    intercept[IllegalArgumentException] {
      ZenWeiConverter.isValidZenAmount(null)
     }

    val negativeZenAmount = BigInteger.valueOf(-10L)
    assertFalse("A negative value is a valid Zen amount", ZenWeiConverter.isValidZenAmount(negativeZenAmount))

    val tooBigZenAmount = BigInteger.valueOf(21000001L).multiply(BigInteger.valueOf(100000000)).multiply(BigInteger.valueOf(10000000000L))//21+ million zen to zennies to wei
    assertFalse("A zen value bigger than max amount of zen is a valid Zen amount", ZenWeiConverter.isValidZenAmount(tooBigZenAmount))

    val notAMultipleOfZennyAmount = BigInteger.valueOf(11000000000L)//1.1 zenny in wei
    assertFalse("A wei value that represents a fractions of zenny is a valid Zen amount", ZenWeiConverter.isValidZenAmount(notAMultipleOfZennyAmount))
  }

  @Test
  def testConvertWeiToZennies(): Unit = {
    intercept[IllegalArgumentException] {
      ZenWeiConverter.convertWeiToZennies(null)
      val notAMultipleOfZennyAmount = BigInteger.valueOf(11000000000L)//1.1 zenny in wei
      ZenWeiConverter.convertWeiToZennies(notAMultipleOfZennyAmount)
    }

    val expectedZenAmount = 10000000
    val zenAmountInWei = BigInteger.valueOf(expectedZenAmount).multiply(BigInteger.valueOf(10000000000L))//0.1 zen to zennies to wei
    assertEquals("Wrong zen amount", expectedZenAmount, ZenWeiConverter.convertWeiToZennies(zenAmountInWei))

  }

  @Test
  def testConvertZenniesToWei(): Unit = {
    val zennyAmount = 23
    val expectedZenAmountInWei = BigInteger.valueOf(zennyAmount).multiply(BigInteger.valueOf(10000000000L))
    assertEquals("Wrong wei amount", expectedZenAmountInWei, ZenWeiConverter.convertZenniesToWei(zennyAmount))

  }

}
