package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeGasPaidValue, log}
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.utils.{BytesUtils, ListSerializer}
import scorex.util.ScorexLogging

import java.math.BigInteger
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success}

abstract class AbstractFakeSmartContractMsgProcessor extends MessageProcessor with ScorexLogging {

  val OP_CODE_LENGTH = 1

  val myAddress: AddressProposition

  @throws[MessageProcessorInitializationException]
  override def init(view: AccountStateView): Unit = {
    if (!view.accountExists(myAddress.address()))
    {
      val codeHash = new Array[Byte](32)
      util.Random.nextBytes(codeHash)

      view.addAccount(myAddress.address(), codeHash)

      log.debug(s"created Message Processor account ${BytesUtils.toHexString(myAddress.address())}")
    }
    else
    {
      log.warn(s"Account ${BytesUtils.toHexString(myAddress.address())} already exists")
    }
  }

  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    myAddress.equals(msg.getTo)
  }

  protected def getFunctionFromData(data: Array[Byte]): Array[Byte] ={
    require(data.length >= OP_CODE_LENGTH, s"Data length ${data.length} must be >= $OP_CODE_LENGTH")
    data.slice(0,OP_CODE_LENGTH)
  }
 }



