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
  val gasUsed: BigInteger = BigInteger.valueOf(21000L)

  override def init(view: AccountStateView): Unit = {
    // No actions required for transferring coins during genesis state initialization.
  }

  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    // Can process only EOA to EOA transfer, so when "to" is an EOA account (no "code" defined).
    // There is no need to check "from" account because it can't be a smart contract one,
    // because there is no known private key to create a valid signature.
    view.getCodeHash(msg.getTo.address()).isEmpty
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
