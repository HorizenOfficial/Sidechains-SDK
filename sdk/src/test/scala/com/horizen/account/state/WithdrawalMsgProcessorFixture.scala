package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.utils.BytesUtils


trait WithdrawalMsgProcessorFixture extends MessageProcessorFixture {

  def getAddWithdrawalRequestMessage(amount: java.math.BigInteger): Message = {
    val params = AddWithdrawalRequestCmdInput(mcAddr).encode()
    val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig), params)
    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, amount, data)
  }

  def getGetListOfWithdrawalRequestMessage(epochNum: Int): Message = {

    val params = GetListOfWithdrawalRequestsInputCmd(epochNum).encode()
    val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig), params)
    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, java.math.BigInteger.ZERO, data)
  }

  val AddNewWithdrawalRequestEventSig = getEventSignature("AddWithdrawalRequestEvent(address,bytes20,uint256,uint32)")
  val NumOfIndexedEvtParams = 2

}
