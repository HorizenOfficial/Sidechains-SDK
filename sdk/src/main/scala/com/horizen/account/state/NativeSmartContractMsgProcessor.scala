package com.horizen.account.state

import com.horizen.account.event.EthereumEvent
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.Address
import sparkz.crypto.hash.Keccak256
import sparkz.util.SparkzLogging

abstract class NativeSmartContractMsgProcessor extends MessageProcessor with SparkzLogging {

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
    msg.getTo.isPresent && contractAddress.sameElements(msg.getToAddressBytes)
  }

  def getEvmLog(event: Any): EvmLog = {
    EthereumEvent.getEvmLog(new Address(BytesUtils.toHexString(contractAddress)), event)
  }
}

object NativeSmartContractMsgProcessor {
  val NULL_HEX_STRING_32: Array[Byte] = Array.fill(32)(0)
}