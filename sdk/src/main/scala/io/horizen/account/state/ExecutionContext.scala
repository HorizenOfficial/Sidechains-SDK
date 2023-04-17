package io.horizen.account.state

trait ExecutionContext {
  val msg: Message
  val blockContext: BlockContext
  def execute(invocation: Invocation): Array[Byte]
}
