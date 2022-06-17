package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.proposition.{MCPublicKeyHashProposition, MCPublicKeyHashPropositionSerializer}
import com.horizen.utils.{BytesUtils, ListSerializer, ZenCoinsUtils}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Keccak256
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success, Try}

object WithdrawalMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val fakeSmartContractAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000011111111111111111111"))

  val getListOfWithdrawalReqsCmdSig: String = "00"
  val addNewWithdrawalReqCmdSig: String = "01"

  val gasSpentForGetListOfWithdrawalReqsCmd: java.math.BigInteger = java.math.BigInteger.ONE
  val gasSpentForAddNewWithdrawalReqCmd: java.math.BigInteger = java.math.BigInteger.ONE
  val gasSpentForAddNewWithdrawalReqFailure: java.math.BigInteger = java.math.BigInteger.ONE
  val gasSpentForGenericFailure: java.math.BigInteger = java.math.BigInteger.ONE

  val MAX_WITHDRAWAL_REQS_NUM_PER_EPOCH = 3900
  val DUST_THRESHOLD_IN_WEI = ZenWeiConverter.convertZenniesToWei(ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE))


  override def canProcess(msg: Message, view: AccountStateView): Boolean = {
    fakeSmartContractAddress.equals(msg.getTo)
  }

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    try {
      if (!canProcess(msg, view)) {
        new InvalidMessage(new IllegalArgumentException(s"Cannot process message $msg"))
      }
      else {

        val functionSig = BytesUtils.toHexString(getOpCodeFromData(msg.getData))
        if (getListOfWithdrawalReqsCmdSig.equals(functionSig)) {
          require(msg.getData.length == OP_CODE_LENGTH + Ints.BYTES, s"Wrong data length ${msg.getData.length}")
          val epochNum = BytesUtils.getInt(msg.getData, OP_CODE_LENGTH)
          val listOfWithdrawalReqs = view.withdrawalRequests(epochNum)

          val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequestBox](WithdrawalRequestBoxSerializer.getSerializer)
          val list = withdrawalRequestSerializer.toBytes(listOfWithdrawalReqs.asJava)
          new ExecutionSucceeded(gasSpentForGetListOfWithdrawalReqsCmd, list)
        }
        else if (addNewWithdrawalReqCmdSig.equals(functionSig)) {
          addWithdrawalRequest(msg, view) match {
            case Success(result) =>
              result
            //log
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

  protected def getListOfWithdrawalReqRecords(epochNum: Int, view: AccountStateView): Seq[Array[Byte]] = ???

  private[horizen] def checkWithdrawalRequestValidity(msg: Message, view: AccountStateView): Unit = {
    val withdrawalAmount = msg.getValue
    val balance = view.getBalance(msg.getFrom.address())
    if (balance.compareTo(withdrawalAmount) < 0) {
      log.error(s"Insufficient balance amount: balance: ${balance}, requested withdrawal amount: $withdrawalAmount")
      throw new IllegalArgumentException("Insufficient balance amount")
    }
    else if (msg.getData.length != OP_CODE_LENGTH + MCPublicKeyHashProposition.KEY_LENGTH) {
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

  }

  protected def addWithdrawalRequest(msg: Message, view: AccountStateView): Try[ExecutionResult] = {
    Try {
      //TODO: check errors in Ethereum, maybe for some kind of errors there a predefined types or codes
      checkWithdrawalRequestValidity(msg, view)
      val currentEpochNum = view.getWithdrawalEpochInfo.epoch
      val key = getWithdrawalEpochCounterKey(currentEpochNum)
      val numOfWithdrawalReqs = Ints.fromByteArray(view.getAccountStorage(fakeSmartContractAddress.address(), key).get)
      if (numOfWithdrawalReqs >= MAX_WITHDRAWAL_REQS_NUM_PER_EPOCH) {
        log.error(s"Reached maximum number of Withdrawal Requests per epoch: request is invalid")
        new ExecutionFailed(gasSpentForAddNewWithdrawalReqFailure, new IllegalArgumentException("Reached maximum number of Withdrawal Requests per epoch"))
      }

      val nextNumOfWithdrawalReqs: Int = numOfWithdrawalReqs + 1
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


  private[horizen] def getWithdrawalEpochCounterKey(withdrawalEpoch: Int): Array[Byte] = {
    calculateKey(Bytes.concat("withdrawalEpochCounter".getBytes, Ints.toByteArray(withdrawalEpoch)))
  }

  private[horizen] def calculateKey(keySeed: Array[Byte]): Array[Byte] = {
    Keccak256.hash(keySeed)

  }

  private[horizen] def getWithdrawalRequestsKey(withdrawalEpoch: Int, counter: Int): Array[Byte] = {
    calculateKey(Bytes.concat("withdrawalRequests".getBytes, Ints.toByteArray(withdrawalEpoch), Ints.toByteArray(counter)))
  }

}

case class WithdrawalRequest(proposition: MCPublicKeyHashProposition, value: java.math.BigInteger) extends BytesSerializable {
  override type M = WithdrawalRequest

  override def serializer: ScorexSerializer[WithdrawalRequest] = WithdrawalRequestSerializer
}

object WithdrawalRequestSerializer extends ScorexSerializer[WithdrawalRequest] {
  override def serialize(obj: WithdrawalRequest, writer: Writer): Unit = {
    MCPublicKeyHashPropositionSerializer.getSerializer.serialize(obj.proposition, writer)
    val byteArray = obj.value.toByteArray
    writer.putUInt(byteArray.length)
    writer.putBytes(byteArray)
  }

  override def parse(reader: Reader): WithdrawalRequest = {
    val proposition = MCPublicKeyHashPropositionSerializer.getSerializer.parse(reader)
    val valueByteArrayLength = reader.getUInt().toInt
    val value = new java.math.BigInteger(reader.getBytes(valueByteArrayLength))
    WithdrawalRequest(proposition, value)

  }
}