package com.horizen.account.state

import com.horizen.account.event.EthereumEvent
import com.horizen.account.proposition.AddressProposition
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.Address
import scorex.util.ScorexLogging

abstract class AbstractFakeSmartContractMsgProcessor extends MessageProcessor with ScorexLogging {

  val NULL_HEX_STRING_32: String = BytesUtils.toHexString(new Array[Byte](32))

  val fakeSmartContractAddress: AddressProposition
  val fakeSmartContractCodeHash: Array[Byte]

  @throws[MessageProcessorInitializationException]
  override def init(view: BaseAccountStateView): Unit = {
    if (!view.accountExists(fakeSmartContractAddress.address())) {
      view.addAccount(fakeSmartContractAddress.address(), fakeSmartContractCodeHash)

      log.debug(s"created Message Processor account ${BytesUtils.toHexString(fakeSmartContractAddress.address())}")
    }
    else {
      val errorMsg = s"Account ${BytesUtils.toHexString(fakeSmartContractAddress.address())} already exists"
      log.error(errorMsg)
      throw new MessageProcessorInitializationException(errorMsg)
    }
  }

  override def canProcess(msg: Message, view: BaseAccountStateView): Boolean = {
    // we rely on the condition that init() has already been called at this point
    fakeSmartContractAddress.equals(msg.getTo)
  }

  def getEvmLog(event: Any): EvmLog = {
    EthereumEvent.getEvmLog(new Address(BytesUtils.toHexString(fakeSmartContractAddress.address())), event)
  }

}



