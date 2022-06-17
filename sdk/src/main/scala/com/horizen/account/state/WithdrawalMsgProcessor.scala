package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.api.http.ZenWeiConverter
import com.horizen.account.proposition.AddressProposition
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.{BytesUtils, ListSerializer, ZenCoinsUtils}
import scorex.crypto.hash.Keccak256

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success, Try}

trait WithdrawalRequestProvider {
  private[horizen] def getListOfWithdrawalReqRecords(epochNum: Int, view: AccountStateView): Seq[WithdrawalRequest]
}

object WithdrawalMsgProcessor extends AbstractFakeSmartContractMsgProcessor with WithdrawalRequestProvider {

  override val fakeSmartContractAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000011111111111111111111"))

  val getListOfWithdrawalReqsCmdSig: String = "00"
  val addNewWithdrawalReqCmdSig: String = "01"

  //TODO Define a proper amount of gas spent for each operation
  val gasSpentForGetListOfWithdrawalReqsCmd: java.math.BigInteger = java.math.BigInteger.ONE
  val gasSpentForGetListOfWithdrawalReqsFailure: java.math.BigInteger = java.math.BigInteger.ONE
  val gasSpentForAddNewWithdrawalReqCmd: java.math.BigInteger = java.math.BigInteger.ONE
  val gasSpentForAddNewWithdrawalReqFailure: java.math.BigInteger = java.math.BigInteger.ONE
  val gasSpentForGenericFailure: java.math.BigInteger = java.math.BigInteger.ONE

  val MAX_WITHDRAWAL_REQS_NUM_PER_EPOCH = 3900
  val DUST_THRESHOLD_IN_WEI: java.math.BigInteger = ZenWeiConverter.convertZenniesToWei(ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE))


  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    fakeSmartContractAddress.equals(msg.getTo)
  }

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    //TODO: check errors in Ethereum, maybe for some kind of errors there a predefined types or codes

    try {
      if (!canProcess(msg, view)) {
        new InvalidMessage(new IllegalArgumentException(s"Cannot process message $msg"))
      }
      else {
        val functionSig = BytesUtils.toHexString(getFunctionFromData(msg.getData))
        if (getListOfWithdrawalReqsCmdSig.equals(functionSig)) {
          getListOfWithdrawalReqRecords(msg, view) match {
            case Success(result) =>
              result
            //TODO log
            case Failure(exception: IllegalArgumentException) =>
              new ExecutionFailed(gasSpentForGetListOfWithdrawalReqsFailure, exception)
            case Failure(exception) =>
              log.error(s"Error while requesting Withdrawal Request list: ${exception.getMessage}", exception)
              new ExecutionFailed(gasSpentForGetListOfWithdrawalReqsFailure, new Exception(exception))
          }
        }
        else if (addNewWithdrawalReqCmdSig.equals(functionSig)) {
          addWithdrawalRequest(msg, view) match {
            case Success(result) =>
              result
            //TODO log
            case Failure(exception: IllegalArgumentException) =>
              new ExecutionFailed(gasSpentForAddNewWithdrawalReqFailure, exception)
            case Failure(exception) =>
              log.error(s"Error while adding a new Withdrawal Request: ${exception.getMessage}", exception)
              new ExecutionFailed(gasSpentForAddNewWithdrawalReqFailure, new Exception(exception))
          }
        }
        else {
          log.error(s"Requested function does not exist. Function signature: $functionSig")
          new ExecutionFailed(gasSpentForGenericFailure, new IllegalArgumentException(s"Requested function does not exist"))
        }
      }
    }
    catch {
      case e: Exception =>
        log.error(s"Exception while processing message: $msg", e)
        new ExecutionFailed(gasSpentForGenericFailure, e)
    }

  }

  private def getWithdrawalEpochCounter(view: AccountStateView, epochNum: Int) = {
    val key = getWithdrawalEpochCounterKey(epochNum)
    val wrCounterInBytes = view.getAccountStorageBytes(fakeSmartContractAddress.address(), key).get
    val numOfWithdrawalReqs = if (wrCounterInBytes.length < Ints.BYTES) {
      0
    }
    else {
      Ints.fromByteArray(wrCounterInBytes)
    }
    numOfWithdrawalReqs
  }

  override private[horizen] def getListOfWithdrawalReqRecords(epochNum: Int, view: AccountStateView): Seq[WithdrawalRequest] = {
    val numOfWithdrawalReqs: Int = getWithdrawalEpochCounter(view, epochNum)

    val listOfWithdrawalReqs = (1 to numOfWithdrawalReqs).map(index => {
      val currentKey = getWithdrawalRequestsKey(epochNum, index)
      WithdrawalRequestSerializer.parseBytes(view.getAccountStorageBytes(fakeSmartContractAddress.address(), currentKey).get)
    })
    listOfWithdrawalReqs
  }

  protected def getListOfWithdrawalReqRecords(msg: Message, view: AccountStateView): Try[ExecutionResult] = {
    Try {
      require(msg.getData.length == OP_CODE_LENGTH + Ints.BYTES, s"Wrong data length ${msg.getData.length}")
      val epochNum = BytesUtils.getInt(msg.getData, OP_CODE_LENGTH)

      val listOfWithdrawalReqs = getListOfWithdrawalReqRecords(epochNum, view)

      val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequest](WithdrawalRequestSerializer)
      val list = withdrawalRequestSerializer.toBytes(listOfWithdrawalReqs.asJava)
      new ExecutionSucceeded(gasSpentForGetListOfWithdrawalReqsCmd, list)
    }

  }



  private[horizen] def checkWithdrawalRequestValidity(msg: Message, view: AccountStateView): Unit = {
    val withdrawalAmount = msg.getValue

    if (msg.getData.length != OP_CODE_LENGTH + MCPublicKeyHashProposition.KEY_LENGTH) {
      log.error(s"Wrong message data field length: ${msg.getData.length}")
      throw new IllegalArgumentException("Wrong message data field length")
    }
    else if (!ZenWeiConverter.isValidZenAmount(withdrawalAmount)) {
      log.error(s"Withdrawal amount is not a valid Zen amount: $withdrawalAmount")
      throw new IllegalArgumentException("Withdrawal amount is not a valid Zen amount")
    }
    else if (withdrawalAmount.compareTo(DUST_THRESHOLD_IN_WEI) < 0) {
      log.error(s"Withdrawal amount is under the dust threshold: $withdrawalAmount")
      throw new IllegalArgumentException("Withdrawal amount is under the dust threshold")
    }
    else {
      val balance = view.getBalance(msg.getFrom.address())
      if (balance.compareTo(withdrawalAmount) < 0) {
        log.error(s"Insufficient balance amount: balance: $balance, requested withdrawal amount: $withdrawalAmount")
        throw new IllegalArgumentException("Insufficient balance amount")
      }
    }

  }

  protected def addWithdrawalRequest(msg: Message, view: AccountStateView): Try[ExecutionResult] = {
    Try {
      checkWithdrawalRequestValidity(msg, view)
      val currentEpochNum = view.getWithdrawalEpochInfo.epoch
      val numOfWithdrawalReqs = getWithdrawalEpochCounter(view, currentEpochNum)
      if (numOfWithdrawalReqs >= MAX_WITHDRAWAL_REQS_NUM_PER_EPOCH) {
        log.error(s"Reached maximum number of Withdrawal Requests per epoch: request is invalid")
        new ExecutionFailed(gasSpentForAddNewWithdrawalReqFailure, new IllegalArgumentException("Reached maximum number of Withdrawal Requests per epoch"))
      }

      val nextNumOfWithdrawalReqs: Int = numOfWithdrawalReqs + 1
      val key = getWithdrawalEpochCounterKey(currentEpochNum)
      view.updateAccountStorageBytes(fakeSmartContractAddress.address(), key, Ints.toByteArray(nextNumOfWithdrawalReqs)).get

      val mcDestAddr = msg.getData.slice(OP_CODE_LENGTH, OP_CODE_LENGTH + MCPublicKeyHashProposition.KEY_LENGTH)
      val withdrawalAmount = msg.getValue
      val request = WithdrawalRequest(new MCPublicKeyHashProposition(mcDestAddr), withdrawalAmount)
      val requestInBytes = request.serializer.toBytes(request)
      view.updateAccountStorageBytes(fakeSmartContractAddress.address(), getWithdrawalRequestsKey(currentEpochNum, nextNumOfWithdrawalReqs), requestInBytes).get

      view.subBalance(msg.getFrom.address(), withdrawalAmount).get
      new ExecutionSucceeded(gasSpentForAddNewWithdrawalReqCmd, requestInBytes)
    }

  }

  private[horizen] def calculateKey(keySeed: Array[Byte]): Array[Byte] = {
    Keccak256.hash(keySeed)
  }

  private[horizen] def getWithdrawalEpochCounterKey(withdrawalEpoch: Int): Array[Byte] = {
    calculateKey(Bytes.concat("withdrawalEpochCounter".getBytes, Ints.toByteArray(withdrawalEpoch)))
  }

  private[horizen] def getWithdrawalRequestsKey(withdrawalEpoch: Int, counter: Int): Array[Byte] = {
    calculateKey(Bytes.concat("withdrawalRequests".getBytes, Ints.toByteArray(withdrawalEpoch), Ints.toByteArray(counter)))
  }

}

