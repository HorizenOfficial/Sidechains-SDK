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
    val gas = BigInteger.valueOf(200000)
    val price = BigInteger.ZERO
    val value = BigInteger.ZERO
    val nonce = BigInteger.ZERO
    new Message(originAddress, to, price, price, price, gas, value, nonce, data)
  }

  def getMessage(to: AddressProposition): Message = getMessage(to, null)
}
