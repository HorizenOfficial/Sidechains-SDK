package com.horizen.account.state

import scorex.util.ScorexLogging

import java.math.BigInteger
import scala.util.Failure

/*
 * EoaMessageProcessor is responsible for management of regular coin transfers inside sidechain.
 * In our case to make a transfer from one user account (EOA account) to another user account.
 */
object EoaMessageProcessor extends MessageProcessor with ScorexLogging {
  //TODO: actual gas to be defined
  val gasUsed: BigInteger = BigInteger.valueOf(20000L)

  override def init(view: AccountStateView): Unit = {
    // No actions required for transferring coins during genesis state initialization.
  }

  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    // Can process only EOA to EOA transfer, so when:
    // 1. "from" account must be an EOA account - no "code" set;
    // 2. transaction "data" is empty.
    msg.getData.isEmpty && view.getCodeHash(msg.getFrom.address()).isEmpty
  }

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    view.subBalance(msg.getFrom.address(), msg.getValue) match {
      case Failure(reason) =>
        log.error(s"Unable to subtract ${msg.getValue} wei from ${msg.getFrom}", reason)
        return new ExecutionFailed(gasUsed, new Exception(reason))
      case _ => // do nothing
    }

    view.addBalance(msg.getTo.address(), msg.getValue) match {
      case Failure(reason) =>
        log.error(s"Unable to add ${msg.getValue} wei to ${msg.getTo}", reason)
        return new ExecutionFailed(gasUsed, new Exception(reason))
      case _ => // do nothing
    }

    new ExecutionSucceeded(gasUsed, Array.emptyByteArray)
  }
}
