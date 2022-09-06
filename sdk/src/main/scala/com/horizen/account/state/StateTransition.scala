package com.horizen.account.state

import com.horizen.account.utils.BigIntegerUtil
import com.horizen.utils.BytesUtils
import scorex.util.ScorexLogging

import java.math.BigInteger

class StateTransition(view: AccountStateView, messageProcessors: Seq[MessageProcessor], blockGasPool: GasPool) extends ScorexLogging {

  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def transition(msg: Message): Array[Byte] = {
    // do preliminary checks
    preCheck(msg)
    // allocate gas for processing this message
    val gasPool = buyGas(msg)
    // consume intrinsic gas
    val intrinsicGas = GasUtil.intrinsicGas(msg.getData, msg.getTo == null)
    if (gasPool.getGas.compareTo(intrinsicGas) < 0) {
      throw IntrinsicGasException(gasPool.getGas, intrinsicGas)
    }
    gasPool.subGas(intrinsicGas)
    // find and execute the first matching processor
    messageProcessors.find(_.canProcess(msg, view)) match {
      case None => throw new IllegalArgumentException("Unable to process message.")
      case Some(processor) =>
        // increase the nonce by 1
        view.increaseNonce(msg.getFrom.address())
        // create a snapshot to rollback to incase of execution errors
        val revisionId = view.snapshot
        try {
          processor.process(msg, view, gasPool)
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

    // call these only once as they are not a simple getters
    val sender = msg.getFrom.address()

    // Check the nonce
    // TODO: skip nonce checks if message is "fake" (RPC calls)
    val stateNonce = view.getNonce(sender)
    val txNonce = msg.getNonce
    val result = txNonce.compareTo(stateNonce)
    if (result < 0) {
      throw NonceTooLowException(sender, txNonce, stateNonce)
    } else if (result > 0) {
      throw NonceTooHighException(sender, txNonce, stateNonce)
    }
    // GETH and therefore StateDB use uint64 to store the nonce and perform an overflow check here using (nonce+1<nonce)
    // BigInteger will not overflow like that, so we just verify that the result after increment still fits into 64 bits
    if (!BigIntegerUtil.isUint64(stateNonce.add(BigInteger.ONE)))
      throw NonceMaxException(sender, stateNonce)

    // Check that the sender is an EOA
    if (!view.isEoaAccount(sender))
      throw SenderNotEoaException(sender, view.getCodeHash(sender))

    // TODO: fee checks if message is "fake" (RPC calls)
    if (msg.getGasFeeCap.compareTo(view.getBaseFeePerGas) < 0)
      throw FeeCapTooLowException(sender, msg.getGasFeeCap, view.getBaseFeePerGas)
  }

  private def buyGas(msg: Message): GasPool = {
    val gas = msg.getGasLimit
    // with a legacy TX gasPrice will be the one set by the caller
    // with an EIP1559 TX gasPrice will be the effective gasPrice (baseFee+tip, capped at feeCap)
    val effectiveFees = gas.multiply(msg.getGasPrice)
    // maxFees is calculated using the feeCap, even if the cap was not reached, i.e. baseFee+tip < feeCap
    val maxFees = if (msg.getGasFeeCap == null) effectiveFees else gas.multiply(msg.getGasFeeCap)
    // make sure the sender has enough balance to cover max fees plus value
    val sender = msg.getFrom.address()
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
    log.debug(s"Prepaying $effectiveFees to sender ${BytesUtils.toHexString(sender)}")
    view.subBalance(sender, effectiveFees)
    // allocate gas for this transaction
    new GasPool(gas)
  }

  private def refundGas(msg: Message, gas: GasPool): Unit = {
    val usedGas = msg.getGasLimit.subtract(gas.getGas)
    // cap gas refund to a quotient of the used gas
    val refGas = view.getRefund.min(usedGas.divide(GasUtil.RefundQuotientEIP3529))
    gas.addGas(refGas)
    // return funds for remaining gas, exchanged at the original rate.
    val remaining = gas.getGas.multiply(msg.getGasPrice)
    val sender = msg.getFrom.address()
    log.debug(s"gas used: $usedGas, remaining gas: ${gas.getGas}, refGas: $refGas, gasPrice: ${msg.getGasPrice}")
    log.debug(s"now refunding $remaining to sender ${BytesUtils.toHexString(sender)}")
    if (remaining.compareTo(BigInteger.ZERO) > 0) {
      view.addBalance(sender, remaining)
      // the addBalance op subtracts gas, we have to restore it here otherwise remaining gas balance is wrong
      if (view.isGasTrackingEnabled())
        gas.addGas(GasUtil.GasTBD)
    }

    // return remaining gas to the gasPool of the current block so it is available for the next transaction
    blockGasPool.addGas(gas.getGas)
  }
}
