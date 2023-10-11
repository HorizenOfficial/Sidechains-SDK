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
    // Can process only EOA to EOA transfer, so when "to" is an EOA account:
    // There is no need to check "from" account because it can't be a smart contract one,
    // because there is no known private key to create a valid signature.
    // Note: in case of smart contract declaration "to" is null.
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
