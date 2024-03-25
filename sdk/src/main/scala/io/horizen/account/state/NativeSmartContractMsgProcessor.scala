package io.horizen.account.state

import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getArgumentsFromData}
import io.horizen.account.state.events.EthereumEvent
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.evm.Address
import sparkz.crypto.hash.Keccak256
import sparkz.util.SparkzLogging

abstract class NativeSmartContractMsgProcessor extends MessageProcessor with SparkzLogging {

  val contractAddress: Address
  val contractCode: Array[Byte]
  lazy val contractCodeHash: Array[Byte] = Keccak256.hash(contractCode)

  @throws[MessageProcessorInitializationException]
  override def init(view: BaseAccountStateView, consensusEpochNumber: Int): Unit = {
    if (!view.accountExists(contractAddress)) {
      view.addAccount(contractAddress, contractCode)
      log.debug(s"created Message Processor account $contractAddress")
    } else {
      val errorMsg = s"Account $contractAddress already exists"
      log.error(errorMsg)
      throw new MessageProcessorInitializationException(errorMsg)
    }
  }

  override def customTracing(): Boolean = false

  override def canProcess(invocation: Invocation, view: BaseAccountStateView, consensusEpochNumber: Int): Boolean = {
    // we rely on the condition that init() has already been called at this point
    invocation.callee.exists(contractAddress.equals(_))
  }

  def getEthereumConsensusDataLog(event: Any): EthereumConsensusDataLog = {
    EthereumEvent.getEthereumConsensusDataLog(contractAddress, event)
  }

  def requireIsNotPayable(invocation: Invocation): Unit = if (invocation.value.signum() != 0) throw new ExecutionRevertedException("Call value must be zero")

  def checkInputDoesntContainParams(invocation: Invocation): Unit = {
    // check we have no other bytes after the op code in the msg data
    if (getArgumentsFromData(invocation.input).length > 0) {
      val msgStr = s"invalid msg data length: ${invocation.input.length}, expected $METHOD_ID_LENGTH"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }
  }

}

object NativeSmartContractMsgProcessor {
  val NULL_HEX_STRING_32: Array[Byte] = Array.fill(32)(0)
}
