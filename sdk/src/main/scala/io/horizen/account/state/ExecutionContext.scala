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
   * Current call depth
   */
  var depth: Int

  /**
   * Manually advance call depth by given amount and continue execution with the given invocation.
   * This is used to update the overall depth when returning from the EVM, in case multiple nested invocations happened
   * there.
   */
  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def executeDepth(invocation: Invocation, additionalDepth: Int): Array[Byte] = {
    depth += additionalDepth
    try {
      execute(invocation)
    } finally {
      depth -= additionalDepth
    }
  }

  /**
   * Process the given invocation within the current context
   */
  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def execute(invocation: Invocation): Array[Byte]
}
