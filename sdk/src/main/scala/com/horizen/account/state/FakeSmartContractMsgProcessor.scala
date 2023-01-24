package com.horizen.account.state

import com.horizen.account.event.EthereumEvent
import com.horizen.evm.interop.EvmLog
import com.horizen.evm.utils.Address
import scorex.crypto.hash.Keccak256
import scorex.util.ScorexLogging

import scala.compat.java8.OptionConverters.RichOptionalGeneric

abstract class FakeSmartContractMsgProcessor extends MessageProcessor with ScorexLogging {

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

  def getEvmLog(event: Any): EvmLog = {
    EthereumEvent.getEvmLog(contractAddress, event)
  }
}

object FakeSmartContractMsgProcessor {
  val NULL_HEX_STRING_32: Array[Byte] = Array.fill(32)(0)
}
