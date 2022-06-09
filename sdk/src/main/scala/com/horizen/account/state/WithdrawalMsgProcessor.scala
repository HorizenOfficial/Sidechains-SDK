package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ListSerializer}
import scorex.util.ScorexLogging

import scala.collection.JavaConverters.{asJavaCollectionConverter, seqAsJavaListConverter}
import scala.util.{Failure, Success}

object WithdrawalMsgProcessor extends MessageProcessor with ScorexLogging {

  val OP_CODE_LENGTH = 1

  val myAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000011111111111111111111"))

  val getListOfWithdrawalReqsCmd: String = "0x00"


  @throws[MessageProcessorInitializationException]
  override def init(view: AccountStateView): Unit = {

    if (view.getAccount(myAddress.address()) == null) {//TODO to check with Jan Christoph
      val codeHash = new Array[Byte](32)
      util.Random.nextBytes(codeHash)

      val account = Account(0, 0, codeHash, new Array[Byte](0))
      val triedAccountStateView = view.addAccount(myAddress.address(), account)

      triedAccountStateView match {
        case Success(_) =>
          log.debug(s"Successfully created Withdrawal Message Processor account $account")

        case Failure(exception) =>
          log.error("Create account for Withdrawal Message Processor failed", exception)
          throw new MessageProcessorInitializationException("Create account for Withdrawal Message Processor failed", exception)

      }

    }

  }

  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    myAddress.equals(msg.getTo)
  }

  protected def getFunctionFromData(data: Array[Byte]): Array[Byte] ={
    require(data.length >= OP_CODE_LENGTH, s"Data length must be >= $OP_CODE_LENGTH")
    data.slice(0,OP_CODE_LENGTH - 1)
  }

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


