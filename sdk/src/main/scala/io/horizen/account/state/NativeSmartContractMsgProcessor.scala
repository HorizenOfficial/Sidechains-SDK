package io.horizen.account.state

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
}

object NativeSmartContractMsgProcessor {
  val NULL_HEX_STRING_32: Array[Byte] = Array.fill(32)(0)
}
