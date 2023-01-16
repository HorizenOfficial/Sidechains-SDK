package com.horizen.account.state

import com.horizen.account.event.EthereumEvent
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.Address
import scorex.crypto.hash.Keccak256
import scorex.util.ScorexLogging

abstract class FakeSmartContractMsgProcessor extends MessageProcessor with ScorexLogging {

  val NULL_HEX_STRING_32: Array[Byte] = Array.fill(32)(0)

  val contractAddress: Array[Byte]
  val contractCode: Array[Byte]
  lazy val contractCodeHash: Array[Byte] = Keccak256.hash(contractCode)

  @throws[MessageProcessorInitializationException]
  override def init(view: BaseAccountStateView): Unit = {
    if (!view.accountExists(contractAddress)) {
      view.addAccount(contractAddress, contractCode)
      log.debug(s"created Message Processor account ${BytesUtils.toHexString(contractAddress)}")
    } else {
      val errorMsg = s"Account ${BytesUtils.toHexString(contractAddress)} already exists"
      log.error(errorMsg)
      throw new MessageProcessorInitializationException(errorMsg)
    }
  }

  override def canProcess(msg: Message, view: BaseAccountStateView): Boolean = {
    // we rely on the condition that init() has already been called at this point
    msg.getTo != null && contractAddress.sameElements(msg.getTo.address())
  }

  def getEvmLog(event: Any): EvmLog = {
    EthereumEvent.getEvmLog(new Address(BytesUtils.toHexString(contractAddress)), event)
  }
}
