package com.horizen.account.state

import com.horizen.account.event.EthereumEvent
import com.horizen.account.receipt.EthereumConsensusDataLog
import com.horizen.evm.Address
import sparkz.crypto.hash.Keccak256
import sparkz.util.SparkzLogging

import scala.compat.java8.OptionConverters.RichOptionalGeneric

abstract class NativeSmartContractMsgProcessor extends MessageProcessor with SparkzLogging {

  val contractAddress: Address
  val contractCode: Array[Byte]
  lazy val contractCodeHash: Array[Byte] = Keccak256.hash(contractCode)

  @throws[MessageProcessorInitializationException]
  override def init(view: BaseAccountStateView): Unit = {
    if (!view.accountExists(contractAddress)) {
      view.addAccount(contractAddress, contractCode)
      log.debug(s"created Message Processor account $contractAddress")
    } else {
      val errorMsg = s"Account $contractAddress already exists"
      log.error(errorMsg)
      throw new MessageProcessorInitializationException(errorMsg)
    }
  }

  override def canProcess(msg: Message, view: BaseAccountStateView): Boolean = {
    // we rely on the condition that init() has already been called at this point
    msg.getTo.asScala.exists(contractAddress.equals(_))
  }

  def getEvmLog(event: Any): EthereumConsensusDataLog = {
    EthereumEvent.getEvmLog(contractAddress, event)
  }
}

object NativeSmartContractMsgProcessor {
  val NULL_HEX_STRING_32: Array[Byte] = Array.fill(32)(0)
}
