package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.proposition.MCPublicKeyHashProposition
import io.horizen.utils.BytesUtils

import java.math.BigInteger

trait WithdrawalMsgProcessorFixture extends MessageProcessorFixture {
  val mcAddr = new MCPublicKeyHashProposition(randomBytes(20))
  val AddNewWithdrawalRequestEventSig: Array[Byte] = getEventSignature(
    "AddWithdrawalRequest(address,bytes20,uint256,uint32)")
  val NumOfIndexedEvtParams = 2

  def addWithdrawalRequestMessage(amount: BigInteger): Message = {
    val params = AddWithdrawalRequestCmdInput(mcAddr).encode()
    val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig), params)
    getMessage(WithdrawalMsgProcessor.contractAddress, amount, data)
  }

  def listWithdrawalRequestsMessage(epochNum: Int): Message = {
    val params = GetListOfWithdrawalRequestsCmdInput(epochNum).encode()
    val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig), params)
    getMessage(WithdrawalMsgProcessor.contractAddress, BigInteger.ZERO, data)
  }
}
