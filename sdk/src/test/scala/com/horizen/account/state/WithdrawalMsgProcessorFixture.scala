package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.account.abi
import com.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.{DefaultFunctionReturnDecoder, TypeReference}

import java.util


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


}
