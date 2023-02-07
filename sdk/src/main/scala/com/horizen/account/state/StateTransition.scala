package com.horizen.account.state

import com.horizen.account.utils.BigIntegerUtil
import sparkz.util.SparkzLogging

import java.math.BigInteger

class StateTransition(
    view: StateDbAccountStateView,
    messageProcessors: Seq[MessageProcessor],
    blockGasPool: GasPool,
    blockContext: BlockContext
) extends SparkzLogging {

  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def transition(msg: Message): Array[Byte] = {
    // do preliminary checks
    preCheck(msg)
    // allocate gas for processing this message
    val gasPool = buyGas(msg)
    // consume intrinsic gas
    val intrinsicGas = GasUtil.intrinsicGas(msg.getData, msg.getTo.isEmpty)
    if (gasPool.getGas.compareTo(intrinsicGas) < 0) throw IntrinsicGasException(gasPool.getGas, intrinsicGas)
    gasPool.subGas(intrinsicGas)
    // reset and prepare account access list
    view.setupAccessList(msg)
    // find and execute the first matching processor
    messageProcessors.find(_.canProcess(msg, view)) match {
      case None =>
        log.error(s"No message processor found for executing message $msg")
        throw new IllegalArgumentException("Unable to process message.")
      case Some(processor) =>
        // increase the nonce by 1
        view.increaseNonce(msg.getFrom)
        // create a snapshot to rollback to in case of execution errors
        val revisionId = view.snapshot
        try {
          processor.process(msg, view, gasPool, blockContext)
        } catch {
          // if the processor throws ExecutionRevertedException we revert all changes
          case err: ExecutionRevertedException =>
            view.revertToSnapshot(revisionId)
            throw err
          // if the processor throws ExecutionFailedException we revert all changes and consume any remaining gas
          case err: ExecutionFailedException =>
            view.revertToSnapshot(revisionId)
            gasPool.subGas(gasPool.getGas)
            throw err
          // any other exception will bubble up and invalidate the block
        } finally {
          refundGas(msg, gasPool)
        }
    }
  }

  private def preCheck(msg: Message): Unit = {
    // We are sure that transaction is semantically valid (so all the tx fields are valid)
    // and was successfully verified by ChainIdBlockSemanticValidator

    val sender = msg.getFrom

    // Check the nonce
    if (!msg.getIsFakeMsg) {
      val stateNonce = view.getNonce(sender)
      msg.getNonce.compareTo(stateNonce) match {
        case x if x < 0 => throw NonceTooLowException(sender, msg.getNonce, stateNonce)
        case x if x > 0 => throw NonceTooHighException(sender, msg.getNonce, stateNonce)
        case _ => // nonce matches
      }
      // GETH and therefore StateDB use uint64 to store the nonce and perform an overflow check here using (nonce+1<nonce)
      // BigInteger will not overflow like that, so we just verify that the result after increment still fits into 64 bits
      if (!BigIntegerUtil.isUint64(stateNonce.add(BigInteger.ONE))) throw NonceMaxException(sender, stateNonce)
      // Check that the sender is an EOA
      if (!view.isEoaAccount(sender))
        throw SenderNotEoaException(sender, view.getCodeHash(sender))
    }

    if (!msg.getIsFakeMsg || msg.getGasFeeCap.bitLength() > 0) {
      if (msg.getGasFeeCap.compareTo(blockContext.baseFee) < 0)
        throw FeeCapTooLowException(sender, msg.getGasFeeCap, blockContext.baseFee)
    }
  }

  private def buyGas(msg: Message): GasPool = {
    val gas = msg.getGasLimit
    // with a legacy TX gasPrice will be the one set by the caller
    // with an EIP1559 TX gasPrice will be the effective gasPrice (baseFee+tip, capped at feeCap)
    val effectiveFees = gas.multiply(msg.getGasPrice)
    // maxFees is calculated using the feeCap, even if the cap was not reached, i.e. baseFee+tip < feeCap
    val maxFees = if (msg.getGasFeeCap == null) effectiveFees else gas.multiply(msg.getGasFeeCap)
    // make sure the sender has enough balance to cover max fees plus value
    val sender = msg.getFrom
    val have = view.getBalance(sender)
    val want = maxFees.add(msg.getValue)
    if (have.compareTo(want) < 0) throw InsufficientFundsException(sender, have, want)
    // deduct gas from gasPool of the current block (unused gas will be returned after execution)
    if (blockGasPool.getGas.compareTo(gas) < 0) {
      // we want to throw the block "gas limit reached" exception here instead of "out of gas"
      // the latter would just fail the current message, but this is an invalid message
      throw GasLimitReached()
    }
    blockGasPool.subGas(gas)
    // prepay effective gas fees
    view.subBalance(sender, effectiveFees)
    // allocate gas for this transaction
    new GasPool(gas)
  }

  private def refundGas(msg: Message, gas: GasPool): Unit = {
    // cap gas refund to a quotient of the used gas
    gas.addGas(view.getRefund.min(gas.getUsedGas.divide(GasUtil.RefundQuotientEIP3529)))
    // return funds for remaining gas, exchanged at the original rate.
    val remaining = gas.getGas.multiply(msg.getGasPrice)
    view.addBalance(msg.getFrom, remaining)
    // return remaining gas to the gasPool of the current block so it is available for the next transaction
    blockGasPool.addGas(gas.getGas)
  }
}
