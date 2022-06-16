package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.utils.BytesUtils
import scorex.util.ScorexLogging

abstract class AbstractFakeSmartContractMsgProcessor extends MessageProcessor with ScorexLogging {

  val OP_CODE_LENGTH = 1

  val fakeSmartContractAddress: AddressProposition

  @throws[MessageProcessorInitializationException]
  override def init(view: AccountStateView): Unit = {
    if (!view.accountExists(fakeSmartContractAddress.address()))
    {
      val codeHash = new Array[Byte](32)
      util.Random.nextBytes(codeHash)

      view.addAccount(fakeSmartContractAddress.address(), codeHash)

      log.debug(s"created Message Processor account ${BytesUtils.toHexString(fakeSmartContractAddress.address())}")
    }
    else
    {
      log.warn(s"Account ${BytesUtils.toHexString(fakeSmartContractAddress.address())} already exists")
    }
  }

  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    fakeSmartContractAddress.equals(msg.getTo)
  }

  protected def getFunctionFromData(data: Array[Byte]): Array[Byte] ={
    require(data.length >= OP_CODE_LENGTH, s"Data length ${data.length} must be >= $OP_CODE_LENGTH")
    data.slice(0,OP_CODE_LENGTH)
  }
 }



