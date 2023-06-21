package io.horizen.account.state

import io.horizen.account.utils.BigIntegerUtil
import io.horizen.evm.EvmContext
import sparkz.util.SparkzLogging

import java.math.BigInteger
import scala.collection.mutable.ListBuffer
import scala.jdk.OptionConverters.RichOptional
import scala.util.{Failure, Success, Try}

class StateTransition(
    view: StateDbAccountStateView,
    messageProcessors: Seq[MessageProcessor],
    blockGasPool: GasPool,
    val blockContext: BlockContext,
    val msg: Message,
) extends SparkzLogging with ExecutionContext {

  // the current stack of invocations
  private val invocationStack = new ListBuffer[Invocation]

  // short hand to access the tracer
  private val tracer = blockContext.getTracer.toScala

  // the current call depth, might be more than invocationStack.length of a message processor handled multiple levels,
  // e.g. multiple internal calls withing the EVM
  var depth = 0

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
  def transition(): Array[Byte] = {
    // do preliminary checks
    preCheck(msg)
    // save the remaining block gas before any changes
    val initialBlockGas = blockGasPool.getGas
    // create a snapshot before any changes are made
    val rollback = view.snapshot
    var skipRefund = false
    // allocate gas for processing this message
    val gasPool = buyGas(msg)
    // trace TX start
    tracer.foreach(_.CaptureTxStart(gasPool.initialGas))
    try {
      // consume intrinsic gas
      val intrinsicGas = GasUtil.intrinsicGas(msg.getData, msg.getTo.isEmpty)
      if (gasPool.getGas.compareTo(intrinsicGas) < 0) throw IntrinsicGasException(gasPool.getGas, intrinsicGas)
      gasPool.subGas(intrinsicGas)
      // reset and prepare account access list
      view.setupAccessList(msg)
      // increase the nonce by 1
      view.increaseNonce(msg.getFrom)
      // execute top-level call frame
      execute(Invocation.fromMessage(msg, gasPool))
    } catch {
      // execution failed was already handled
      case err: ExecutionFailedException => throw err
      // any other exception will bubble up and invalidate the block
      case err: Exception =>
        // do not process refunds in this case, all changes will be reverted
        skipRefund = true
        // revert all changes, even buying gas and increasing the nonce
        view.revertToSnapshot(rollback)
        // revert any changes to the block gas pool
        blockGasPool.addGas(initialBlockGas.subtract(blockGasPool.getGas))
        throw err
    } finally {
      if (!skipRefund) refundGas(msg, gasPool)
      // trace TX end
      tracer.foreach(_.CaptureTxEnd(gasPool.getGas))
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

  /**
   * Execute given invocation on the current call stack.
   */
  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def execute(invocation: Invocation): Array[Byte] = {
    // limit call depth to 1024
    if (depth >= 1024) throw new ExecutionRevertedException("Maximum depth of call stack reached")
    // get caller gas pool, for the top-level call this is empty
    // TODO: this will be wrong after a call to `callDepth()` because it does not add a new invocation to the stack
    //  as gas can only ever decrease using a gaspool "too high" up the stack will only ever "too much" gas, i.e. this
    //  will never throw a false out-of-gas error, but the gas limit is not correctly checked
    val callerGas = invocationStack.headOption.map(_.gasPool)
    // allocate gas from caller to the nested invocation, this can throw if the caller does not have enough gas
    callerGas.foreach(_.subGas(invocation.gasPool.getGas))
    // enable write protection if it is not already on
    // every nested invocation after a read-only invocation must remain read-only
    val firstReadOnlyInvocation = invocation.readOnly && !view.readOnly
    if (firstReadOnlyInvocation) view.enableWriteProtection()
    try {
      // Verify that there is no value transfer during a read-only invocation. This would also throw later because
      // view.addBalance and view.subBalance would throw, this just makes it fail faster.
      if (invocation.readOnly && invocation.value.signum() != 0) {
        throw new WriteProtectionException("invalid value transfer during read-only invocation");
      }
      // find and execute the first matching processor
      messageProcessors.find(_.canProcess(invocation, view)) match {
        case None =>
          log.error(s"No message processor found for invocation: $invocation")
          throw new IllegalArgumentException("Unable to execute invocation.")
        case Some(processor) => invoke(processor, invocation)
      }
    } finally {
      // disable write protection only if it was disabled before this invocation
      if (firstReadOnlyInvocation) view.disableWriteProtection()
      // return remaining gas to the caller
      callerGas.foreach(_.addGas(invocation.gasPool.getGas))
    }
  }

  private def invoke(processor: MessageProcessor, invocation: Invocation): Array[Byte] = {
    val startTime = System.nanoTime()
    if (!processor.customTracing()) {
      tracer.foreach(tracer => {
        if (depth == 0) {
          // trace start of top-level call frame
          val context = new EvmContext
          context.chainID = BigInteger.valueOf(blockContext.chainID)
          context.coinbase = blockContext.forgerAddress
          context.gasLimit = blockContext.blockGasLimit
          context.gasPrice = msg.getGasPrice
          context.blockNumber = BigInteger.valueOf(blockContext.blockNumber)
          context.time = BigInteger.valueOf(blockContext.timestamp)
          context.baseFee = blockContext.baseFee
          context.random = blockContext.random
          tracer.CaptureStart(
            view.getStateDbHandle,
            context,
            invocation.caller,
            invocation.callee.orNull,
            invocation.callee.isEmpty,
            invocation.input,
            invocation.gasPool.initialGas,
            invocation.value
          )
        } else {
          // trace start of nested call frame
          tracer.CaptureEnter(
            invocation.guessOpCode(),
            invocation.caller,
            invocation.callee.orNull,
            invocation.input,
            invocation.gasPool.initialGas,
            invocation.value
          )
        }
      })
    }

    // add new invocation to the stack
    invocationStack.prepend(invocation)
    // increase call depth
    depth += 1
    // create a snapshot before any changes are made by the processor
    val revert = view.snapshot
    // execute the message processor
    val result = Try.apply(processor.process(invocation, view, this))
    // handle errors
    result match {
      // if the processor throws ExecutionRevertedException we revert changes
      case Failure(_: ExecutionRevertedException) =>
        view.revertToSnapshot(revert)
      // if the processor throws ExecutionFailedException we revert changes and consume any remaining gas
      case Failure(_: ExecutionFailedException) =>
        view.revertToSnapshot(revert)
        invocation.gasPool.subGas(invocation.gasPool.getGas)
      // other errors will be handled further up the stack
      case _ =>
    }
    // reduce call depth
    depth -= 1
    // remove the current invocation from the stack
    invocationStack.remove(0)

    if (!processor.customTracing()) {
      tracer.foreach(tracer => {
        val output = result match {
          // revert reason returned from message processor
          case Failure(err: ExecutionRevertedException) => err.returnData
          // successful result
          case Success(value) => value
          // other errors do not have a return value
          case _ => Array.emptyByteArray
        }
        // get error message if any
        val error = result match {
          case Failure(exception) => exception.getMessage
          case _ => ""
        }
        if (depth == 0) {
          // trace end of top-level call frame
          tracer.CaptureEnd(output, invocation.gasPool.getUsedGas, System.nanoTime() - startTime, error)
        } else {
          // trace end of nested call frame
          tracer.CaptureExit(output, invocation.gasPool.getUsedGas, error)
        }
      })
    }

    // note: this will either return the output of the message processor
    // or rethrow any exception caused during execution
    result.get
  }
}
