package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.utils.BytesUtils

import java.math.BigInteger

trait EvmMessageProcessorTestBase {

  private def toProposition(hex: String) = new AddressProposition(BytesUtils.fromHexString(hex))

  val originAddress: AddressProposition = toProposition("00000000000000000000000000000000FFFFFF01")
  val emptyAddress: AddressProposition = toProposition("00000000000000000000000000000000FFFFFF02")
  val eoaAddress: AddressProposition = toProposition("00000000000000000000000000000000FFFFFF03")
  val contractAddress: AddressProposition = toProposition("00000000000000000000000000000000FFFFFF04")

  def getMessage(to: AddressProposition, data: Array[Byte]): Message = {
    val gasLimit = BigInteger.valueOf(1000000)
    val gasPrice = BigInteger.ZERO
    val gasFeeCap = BigInteger.valueOf(1000001)
    val gasTipCap = BigInteger.ZERO
    val value = BigInteger.ZERO
    val nonce = BigInteger.ZERO
    new Message(originAddress, to, gasPrice, gasFeeCap, gasTipCap, gasLimit, value, nonce, data)
  }

  def getMessage(to: AddressProposition): Message = getMessage(to, null)
}
