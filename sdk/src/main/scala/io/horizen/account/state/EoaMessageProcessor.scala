package io.horizen.account.state

import sparkz.util.SparkzLogging

/*
 * EoaMessageProcessor is responsible for management of regular coin transfers inside sidechain.
 * In our case to make a transfer from one user account (EOA account) to another user account.
 */
object EoaMessageProcessor extends MessageProcessor with SparkzLogging {
  override def init(view: BaseAccountStateView, consensusEpochNumber: Int): Unit = {
    // No actions required for transferring coins during genesis state initialization.
  }

  override def customTracing(): Boolean = false

  override def canProcess(invocation: Invocation, view: BaseAccountStateView, consensusEpochNumber: Int): Boolean = {
    // Can process EOA to EOA and Native contract to EOA transfer
    invocation.callee.exists(view.isEoaAccount)
  }

  @throws(classOf[ExecutionFailedException])
  override def process(
      invocation: Invocation,
      view: BaseAccountStateView,
      context: ExecutionContext
  ): Array[Byte] = {
    view.subBalance(invocation.caller, invocation.value)
    view.addBalance(invocation.callee.get, invocation.value)
    Array.emptyByteArray
  }
}
