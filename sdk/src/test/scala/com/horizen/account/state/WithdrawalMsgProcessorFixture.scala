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

  def decodeListOfWithdrawalRequest(wrListInBytes: Array[Byte]): util.List[abi.util.WithdrawalRequest] = {
    val decoder = new DefaultFunctionReturnDecoder()
    val typeRef1 = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[DynamicArray[abi.util.WithdrawalRequest]]() {}))
    val listOfWR = decoder.decodeFunctionResult(org.web3j.utils.Numeric.toHexString(wrListInBytes), typeRef1)
    listOfWR.get(0).asInstanceOf[DynamicArray[abi.util.WithdrawalRequest]].getValue
  }

  def decodeWithdrawalRequest(wrInBytes: Array[Byte]): abi.util.WithdrawalRequest = {
    val decoder = new DefaultFunctionReturnDecoder()
    val typeRef = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[abi.util.WithdrawalRequest]() {}))
    val wr = decoder.decodeFunctionResult(org.web3j.utils.Numeric.toHexString(wrInBytes), typeRef)
    wr.get(0).asInstanceOf[abi.util.WithdrawalRequest]
  }

}
