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
  val GAS_USED: BigInteger = BigInteger.valueOf(21000L)

  override def init(view: BaseAccountStateView): Unit = {
    // No actions required for transferring coins during genesis state initialization.
  }

  override def canProcess(msg: Message, view: BaseAccountStateView): Boolean = {
    // Can process only EOA to EOA transfer, so when "to" is an EOA account:
    // There is no need to check "from" account because it can't be a smart contract one,
    // because there is no known private key to create a valid signature.
    // Note: in case of smart contract declaration "to" is null.
    msg.getTo != null && view.isEoaAccount(msg.getTo.address())
  }

  override def process(msg: Message, view: BaseAccountStateView): ExecutionResult = {
    view.subBalance(msg.getFrom.address(), msg.getValue) match {
      case Failure(reason) =>
        log.error(s"Unable to subtract ${msg.getValue} wei from ${msg.getFrom}", reason)
        return new ExecutionFailed(GAS_USED, new Exception(reason))
      case _ => // do nothing
    }

    view.addBalance(msg.getTo.address(), msg.getValue) match {
      case Failure(reason) =>
        log.error(s"Unable to add ${msg.getValue} wei to ${msg.getTo}", reason)
        return new ExecutionFailed(GAS_USED, new Exception(reason))
      case _ => // do nothing
    }

    new ExecutionSucceeded(GAS_USED, Array.emptyByteArray)
  }
}
