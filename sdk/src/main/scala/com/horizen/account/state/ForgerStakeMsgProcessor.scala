package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.utils.{BytesUtils, ListSerializer}

import scala.collection.JavaConverters.seqAsJavaListConverter

object ForgerStakeMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val myAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00000000000000000000222222222222222222"))

  val getListOfForgersCmd: String = "0x00"
  val AddNewStakeCmd: String =      "0x01"
  val RemoveStakeCmd: String =      "0x02"

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    try {
      val cmdString = BytesUtils.toHexString(getFunctionFromData(msg.getData))
      cmdString match {
        case `getListOfForgersCmd` =>

          // we do not have any argument here
          require(msg.getData.length == OP_CODE_LENGTH, s"Wrong data length ${msg.getData.length}")


          val listOfForgers = Array[Byte]() //view.getForgerList


          new ExecutionSucceeded(java.math.BigInteger.ONE, listOfForgers)
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


