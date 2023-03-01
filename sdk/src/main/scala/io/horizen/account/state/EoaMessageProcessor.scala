package io.horizen.account.state

import sparkz.util.SparkzLogging

import scala.compat.java8.OptionConverters.RichOptionalGeneric

/*
 * EoaMessageProcessor is responsible for management of regular coin transfers inside sidechain.
 * In our case to make a transfer from one user account (EOA account) to another user account.
 */
object EoaMessageProcessor extends MessageProcessor with SparkzLogging {
  override def init(view: BaseAccountStateView): Unit = {
    // No actions required for transferring coins during genesis state initialization.
  }

  override def canProcess(msg: Message, view: BaseAccountStateView): Boolean = {
    // Can process only EOA to EOA transfer, so when "to" is an EOA account:
    // There is no need to check "from" account because it can't be a smart contract one,
    // because there is no known private key to create a valid signature.
    // Note: in case of smart contract declaration "to" is null.
    msg.getTo.asScala.exists(view.isEoaAccount)
  }

  @throws(classOf[ExecutionFailedException])
  override def process(
      msg: Message,
      view: BaseAccountStateView,
      gas: GasPool,
      blockContext: BlockContext
  ): Array[Byte] = {
    view.subBalance(msg.getFrom, msg.getValue)
    view.addBalance(msg.getTo.get(), msg.getValue)
    Array.emptyByteArray
  }
}
