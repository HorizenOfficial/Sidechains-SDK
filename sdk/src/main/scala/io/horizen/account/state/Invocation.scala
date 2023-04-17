package io.horizen.account.state

import io.horizen.evm.Address

import java.math.BigInteger

case class Invocation(
    caller: Address,
    callee: Option[Address],
    value: BigInteger,
    input: Array[Byte],
    gas: GasPool,
    readOnly: Boolean,
)

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
