package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.utils.{BytesUtils, ListSerializer}
import scorex.util.ScorexLogging

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success}

abstract class AbstractFakeSmartContractMsgProcessor extends MessageProcessor with ScorexLogging {

  val OP_CODE_LENGTH = 1

  val myAddress: AddressProposition

  @throws[MessageProcessorInitializationException]
  override def init(view: AccountStateView): Unit = {

    if (view.getAccount(myAddress.address()) == null) {
      //TODO to check with Jan Christoph: if the get fails it creates an empty one?
      val codeHash = new Array[Byte](32)
      util.Random.nextBytes(codeHash)

      val account = Account(0, 0, codeHash, new Array[Byte](0))
      val triedAccountStateView = view.addAccount(myAddress.address(), account)

      triedAccountStateView match {
        case Success(_) =>
          log.debug(s"Successfully created Message Processor account $account")

        case Failure(exception) =>
          log.error("Create account for Message Processor failed", exception)
          throw new MessageProcessorInitializationException("Create account for Message Processor failed", exception)
      }
    }
  }

  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    myAddress.equals(msg.getTo)
  }

  protected def getFunctionFromData(data: Array[Byte]): Array[Byte] ={
    require(data.length >= OP_CODE_LENGTH, s"Data length must be >= $OP_CODE_LENGTH")
    data.slice(0,OP_CODE_LENGTH)
  }
}


