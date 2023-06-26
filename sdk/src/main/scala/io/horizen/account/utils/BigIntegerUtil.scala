package io.horizen.account.utils

import java.math.BigInteger

object BigIntegerUtil {
  def isUint64(number: BigInteger): Boolean = number.signum() >= 0 && number.bitLength() <= 64
  def isUint256(number: BigInteger): Boolean = number.signum() >= 0 && number.bitLength() <= 256
  def toUint256Bytes(number: BigInteger): Array[Byte] = {
    val padded = new Array[Byte](32)
    // BigInteger.toByteArray always contains the sign bit, so e.g. if the number itself is 256-bit,
    // with the sign bit it will be 257-bit and therefore contain a zero byte (assuming the number is positive)
    // at the start which we need to drop
    val bytes = number.toByteArray.dropWhile(_ == 0)
    // pad the result to 32 bytes, adding zeros at the start if necessary
    bytes.copyToArray(padded, padded.length - bytes.length)
    padded
  }
}
