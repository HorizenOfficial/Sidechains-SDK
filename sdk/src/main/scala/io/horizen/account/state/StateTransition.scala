package io.horizen.account.state

import com.horizen.account.utils.BigIntegerUtil
import sparkz.util.SparkzLogging

import java.math.BigInteger

class StateTransition(
    view: StateDbAccountStateView,
    messageProcessors: Seq[MessageProcessor],
    blockGasPool: GasPool,
    blockContext: BlockContext
) extends SparkzLogging {

  /**
   * Perform a state transition by applying the given message to the current state view. Afterwards, the state will
   * always be in a consistent state, possible outcomes are:
   *   - The message was applied successfully, return value is the data returned by the executed message processor
   *   - The message processor aborted by throwing any ExecutionFailedException (e.g. also ExecutionRevertedException).
   *     This means the message is valid but application failed. Any changes by the message processor are reverted, but
   *     the senders nonce is incremented and used gas is still paid.
   *   - Any other exception was thrown: Any and all changes are reverted. This means the message is invalid.
   */
  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def transition(msg: Message): Array[Byte] = {
    // do preliminary checks
    preCheck(msg)
    // save the remaining block gas before any changes
    val initialBlockGas = blockGasPool.getGas
    // create a snapshot before any changes are made
    val initialRevision = view.snapshot
    try {
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
          // create a snapshot before any changes are made by the processor
          val revisionProcessor = view.snapshot
          var skipRefund = false
          try {
            processor.process(msg, view, gasPool, blockContext)
          } catch {
            // if the processor throws ExecutionRevertedException we revert changes
            case err: ExecutionRevertedException =>
              view.revertToSnapshot(revisionProcessor)
              throw err
            // if the processor throws ExecutionFailedException we revert changes and consume any remaining gas
            case err: ExecutionFailedException =>
              view.revertToSnapshot(revisionProcessor)
              gasPool.subGas(gasPool.getGas)
              throw err
            case err : Throwable =>
              // do not process refunds in this case, all changes will be reverted
              skipRefund = true
              throw err
          } finally {
            if (!skipRefund) refundGas(msg, gasPool)
          }
      }
    } catch {
      // execution failed was already handled
      case err: ExecutionFailedException => throw err
      // any other exception will bubble up and invalidate the block
      case err: Throwable =>
        // revert all changes, even buying gas and increasing the nonce
        view.revertToSnapshot(initialRevision)
        // revert any changes to the block gas pool
        blockGasPool.addGas(initialBlockGas.subtract(blockGasPool.getGas))
        throw err
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
      // the latter would just result in a failed message, but this message must not be applied at all
      // either the block is full or this message tries to use more gas than the block gas limit
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
