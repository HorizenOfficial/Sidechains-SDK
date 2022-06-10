package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.utils.{BytesUtils, ListSerializer}

import scala.collection.JavaConverters.seqAsJavaListConverter

object WithdrawalMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val myAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000011111111111111111111"))

  val getListOfWithdrawalReqsCmd: String = "0x00"

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    try {
      val function = getFunctionFromData(msg.getData)
      if (getListOfWithdrawalReqsCmd.equals(BytesUtils.toHexString(function))){
        require(msg.getData.length == OP_CODE_LENGTH + 4, s"Wrong data length ${msg.getData.length}")
        val epochNum = BytesUtils.getInt(msg.getData,OP_CODE_LENGTH)
        val listOfWithdrawalReqs = view.withdrawalRequests(epochNum)

        val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequestBox](WithdrawalRequestBoxSerializer.getSerializer)
        val list = withdrawalRequestSerializer.toBytes(listOfWithdrawalReqs.asJava)
        new ExecutionSucceeded(java.math.BigInteger.ONE, list)
      }
      else if (false){
        new ExecutionSucceeded(java.math.BigInteger.ONE, null)

      }
      new InvalidMessage(null)

    }
    catch {
      case e : Exception =>
        log.error(s"Exception while processing message: $msg",e)
        new InvalidMessage(e)
    }
  }
}


