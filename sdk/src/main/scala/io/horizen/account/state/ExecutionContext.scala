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
