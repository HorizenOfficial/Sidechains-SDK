package com.horizen.account.state

import com.google.common.primitives.Ints
import com.horizen.account.api.http.ZenWeiConverter
import com.horizen.account.proposition.AddressProposition
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.utils.{BytesUtils, ListSerializer, ZenCoinsUtils}

import scala.collection.JavaConverters.seqAsJavaListConverter

object WithdrawalMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val myAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000011111111111111111111"))

  val getListOfWithdrawalReqsCmdSig: String = "00"
  val addNewWithdrawalReqCmdSig: String = "01"

  val gasSpentForGetListOfWithdrawalReqsCmd: java.math.BigInteger  = java.math.BigInteger.ONE
  val gasSpentForAddNewWithdrawalReqCmd: java.math.BigInteger = java.math.BigInteger.ONE

  val WT_DEST_ADDR_LENGTH = 20


  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    myAddress.equals(msg.getTo)
  }

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    try {
      val functionSig = BytesUtils.toHexString(getFunctionFromData(msg.getData))
      if (getListOfWithdrawalReqsCmdSig.equals(functionSig)){
        require(msg.getData.length == OP_CODE_LENGTH + Ints.BYTES, s"Wrong data length ${msg.getData.length}")
        val epochNum = BytesUtils.getInt(msg.getData,OP_CODE_LENGTH)
        val listOfWithdrawalReqs = view.withdrawalRequests(epochNum)

        val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequestBox](WithdrawalRequestBoxSerializer.getSerializer)
        val list = withdrawalRequestSerializer.toBytes(listOfWithdrawalReqs.asJava)
        new ExecutionSucceeded(gasSpentForGetListOfWithdrawalReqsCmd, list)
      }
      else if (addNewWithdrawalReqCmdSig.equals(functionSig)){
        require(msg.getData.length == OP_CODE_LENGTH + WT_DEST_ADDR_LENGTH, s"Wrong data length ${msg.getData.length}")
//        check that tx.value % 10^10 = 0 && tx.value / 10^10 > 54 && num(Bt, current withdrawal epoch) < 3900 (min BT amount supported)
        if (isWithdrawalRequestValid(msg)){

        }
        else {
          //        Otherwise stop execution. Transaction is valid, but does nothing (only gas is payed).
        }
        val destAddr = msg.getData.slice(OP_CODE_LENGTH, OP_CODE_LENGTH + WT_DEST_ADDR_LENGTH)

        new ExecutionSucceeded(gasSpentForAddNewWithdrawalReqCmd, null)

      }
      else {
        log.error(s"Requested function does not exist: $functionSig")
        new InvalidMessage(new IllegalArgumentException(s"Requested function does not exist: $functionSig"))
      }
   }
    catch {
      case e : Exception =>
        log.error(s"Exception while processing message: $msg",e)
        new InvalidMessage(e)
    }
  }

  def isWithdrawalRequestValid(msg: Message): Boolean = {
    if(ZenWeiConverter.isValidZenAmount(msg.getValue) &&
      ZenWeiConverter.convertWeiToZennies(msg.getValue) >= ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)){

   }
    false
  }

}


