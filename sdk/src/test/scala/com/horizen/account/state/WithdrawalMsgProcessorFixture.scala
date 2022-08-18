package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.utils.BytesUtils

import java.math.BigInteger

trait WithdrawalMsgProcessorFixture extends MessageProcessorFixture {

  val AddNewWithdrawalRequestEventSig: Array[Byte] = getEventSignature(
    "AddWithdrawalRequest(address,bytes20,uint256,uint32)")
  val NumOfIndexedEvtParams = 2

  def getAddWithdrawalRequestMessage(amount: BigInteger): Message = {
    val params = AddWithdrawalRequestCmdInput(mcAddr).encode()
    val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig), params)
    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, amount, data)
  }

  def getGetListOfWithdrawalRequestMessage(epochNum: Int): Message = {
    val params = GetListOfWithdrawalRequestsInputCmd(epochNum).encode()
    val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig), params)
    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, BigInteger.ZERO, data)
  }

}
