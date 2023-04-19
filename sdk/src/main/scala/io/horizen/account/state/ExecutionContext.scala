package io.horizen.account.state

trait ExecutionContext {
  /**
   * The original message currently being processed
   */
  val msg: Message

  /**
   * Contextual information
   */
  val blockContext: BlockContext

  /**
   * Process the given invocation within the current context
   */
  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def execute(invocation: Invocation): Array[Byte]
}
