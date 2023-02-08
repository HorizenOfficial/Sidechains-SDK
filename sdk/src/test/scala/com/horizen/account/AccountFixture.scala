package com.horizen.account

import com.horizen.evm.utils.Address

import java.math.BigInteger
import scala.language.implicitConversions
import scala.util.Random

trait AccountFixture {
  // simplifies using BigIntegers within the tests
  implicit def longToBigInteger(x: Long): BigInteger = BigInteger.valueOf(x)

  def randomBytes(n: Int): Array[Byte] = {
    val bytes = new Array[Byte](n)
    val rand = new Random()
    rand.nextBytes(bytes)
    bytes
  }

  def randomU256: BigInteger = new BigInteger(randomBytes(32))

  def randomHash: Array[Byte] = randomBytes(32)

  def randomAddress: Address = new Address(randomBytes(Address.LENGTH))
}
