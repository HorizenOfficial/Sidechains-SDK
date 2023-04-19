package io.horizen.account.state

case class TestContext(msg: Message, blockContext: BlockContext) extends ExecutionContext {
  override def execute(invocation: Invocation): Array[Byte] = Array.emptyByteArray
}

object TestContext {
  /**
   * Creates a top level invocation from the given message and calls "canProcess" on the message processor.
   */
  def canProcess(processor: MessageProcessor, msg: Message, view: BaseAccountStateView): Boolean = {
    processor.canProcess(Invocation.fromMessage(msg), view)
  }

  /**
   * Creates a top level invocation from the given message and executes it with the message processor.
   */
  def process(
      processor: MessageProcessor,
      msg: Message,
      view: BaseAccountStateView,
      blockContext: BlockContext,
      gasPool: GasPool
  ): Array[Byte] = {
    processor.process(Invocation.fromMessage(msg, gasPool), view, TestContext(msg, blockContext))
  }
}
