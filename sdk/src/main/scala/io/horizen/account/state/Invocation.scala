package io.horizen.account.state

import io.horizen.evm.{Address, TracerOpCode}
import io.horizen.utils.BytesUtils
import org.web3j.utils.Numeric

import java.math.BigInteger
import scala.compat.java8.OptionConverters.RichOptionalGeneric

case class Invocation(
    caller: Address,
    callee: Option[Address],
    value: BigInteger,
    input: Array[Byte],
    gasPool: GasPool,
    readOnly: Boolean,
) {

  /**
   * Create nested invocation, similar to an EVM "CALL".
   *
   * @param addr
   *   address to call
   * @param value
   *   amount of funds to transfer
   * @param input
   *   calldata arguments
   * @param gas
   *   amount of gas to allocate to the nested call, should not be greater than remaining gas in the parent invocation
   *   (will be validated in StateTransition.execute)
   * @return
   *   new invocation object
   */
  def call(addr: Address, value: BigInteger, input: Array[Byte], gas: BigInteger): Invocation = {
    Invocation(callee.get, Some(addr), value, input, new GasPool(gas), readOnly = false)
  }

  /**
   * Create nested read-only invocation, similar to an EVM "STATICCALL".
   *
   * @param addr
   *   address to call
   * @param input
   *   calldata arguments
   * @param gas
   *   amount of gas to allocate to the nested call, should not be greater than remaining gas in the parent invocation
   *   (will be validated in StateTransition.execute)
   * @return
   */
  def staticCall(addr: Address, input: Array[Byte], gas: BigInteger): Invocation = {
    Invocation(callee.get, Some(addr), BigInteger.ZERO, input, new GasPool(gas), readOnly = true)
  }

  def guessOpCode(): TracerOpCode = {
    if (callee.isEmpty) {
      TracerOpCode.CREATE
    } else if (readOnly) {
      TracerOpCode.STATICCALL
    } else {
      TracerOpCode.CALL
    }
  }

  override def toString: String =
    "%s{caller=%s, callee=%s, value=%s, input=%s, gasPool.getUsedGas=%s, readOnly=%s}"
      .format(
        this.getClass.toString,
        caller.toString,
        if (callee.isEmpty) "" else callee.get.toString,
        if (value != null) Numeric.toHexStringWithPrefix(value) else "null",
        if (input != null) BytesUtils.toHexString(input),
        if (gasPool!=null) gasPool.getUsedGas.toString else "null",
        if (readOnly) "YES" else "NO"
      )


//  def traceTopLevel(tracer: Tracer): Unit = {}
}

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
