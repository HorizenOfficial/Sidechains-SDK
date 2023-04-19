package io.horizen.account.state

import io.horizen.evm.Address

import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionalGeneric

case class Invocation(
    caller: Address,
    callee: Option[Address],
    value: BigInteger,
    input: Array[Byte],
    gas: GasPool,
    readOnly: Boolean,
)

object Invocation {
  /**
   * Create top level invocation from a message.
   */
  def fromMessage(msg: Message, gasPool: GasPool): Invocation =
    Invocation(msg.getFrom, msg.getTo.asScala, msg.getValue, msg.getData, gasPool, readOnly = false)

  /**
   * Create top level invocation from a message, without any gas. Mostly useful for tests.
   */
  def fromMessage(msg: Message): Invocation =
    Invocation(
      msg.getFrom,
      msg.getTo.asScala,
      msg.getValue,
      msg.getData,
      new GasPool(BigInteger.ZERO),
      readOnly = false
    )
}

//case class Invocation(callee: Address, input: Array[Byte], gas: GasPool)
//case class Call() extends Invocation()
//case class StaticCall() extends Invocation()
//case class CallCode() extends Invocation()
//case class DelegateCall() extends Invocation()

//object Invocation {
//  def call(caller: Address, callee: Address, value: BigInteger, input: Array[Byte], gas: GasPool): Invocation =
//    Invocation(caller, Some(callee), value, input, gas, readOnly = false)
//
//  def staticCall(caller: Address, callee: Address, input: Array[Byte], gas: GasPool): Invocation =
//    Invocation(caller, Some(callee), BigInteger.ZERO, input, gas, readOnly = true)
//
//  def delegateCall(
//      original: Invocation,
//      callee: Address,
//      input: Array[Byte],
//      gas: GasPool
//  ): Invocation =
//    Invocation(original.caller, Some(callee), original.value, input, gas, readOnly = false)
//}
