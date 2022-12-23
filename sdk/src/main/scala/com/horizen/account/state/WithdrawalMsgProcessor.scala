package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.abi.ABIUtil.{METHOD_CODE_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import com.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder}
import com.horizen.account.events.AddWithdrawalRequest
import com.horizen.account.utils.WellKnownAddresses.WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS_BYTES
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.ZenCoinsUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes20, Uint32}
import org.web3j.abi.datatypes.{StaticStruct, Type}
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter

trait WithdrawalRequestProvider {
  private[horizen] def getListOfWithdrawalReqRecords(epochNum: Int, view: BaseAccountStateView): Seq[WithdrawalRequest]
}

object WithdrawalMsgProcessor extends FakeSmartContractMsgProcessor with WithdrawalRequestProvider {

  override val contractAddress: Array[Byte] = WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS_BYTES
  override val contractCode: Array[Byte] = Keccak256.hash("WithdrawalRequestSmartContractCode")

  val GetListOfWithdrawalReqsCmdSig: String = getABIMethodId("getWithdrawalRequests(uint32)")
  val AddNewWithdrawalReqCmdSig: String = getABIMethodId("submitWithdrawalRequest(bytes20)")

  //TODO Define a proper amount of gas spent for each operation
  val GasSpentForGetListOfWithdrawalReqsCmd: BigInteger = BigInteger.ONE
  val GasSpentForAddNewWithdrawalReqCmd: BigInteger = BigInteger.ONE

  val MaxWithdrawalReqsNumPerEpoch = 3999
  val DustThresholdInWei: BigInteger = ZenWeiConverter.convertZenniesToWei(ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE))

  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    //TODO: check errors in Ethereum, maybe for some kind of errors there a predefined types or codes
    val gasView = new AccountStateViewGasTracked(view, gas)
    getFunctionSignature(msg.getData) match {
      case GetListOfWithdrawalReqsCmdSig =>
        gas.subGas(GasSpentForGetListOfWithdrawalReqsCmd)
        execGetListOfWithdrawalReqRecords(msg, gasView)

      case AddNewWithdrawalReqCmdSig =>
        gas.subGas(GasSpentForAddNewWithdrawalReqCmd)
        execAddWithdrawalRequest(msg, gasView, blockContext.withdrawalEpochNumber)

      case functionSig =>
        throw new ExecutionRevertedException(s"Requested function does not exist. Function signature: $functionSig")
    }
  }

  private def getWithdrawalEpochCounter(view: BaseAccountStateView, epochNum: Int) = {
    val key = getWithdrawalEpochCounterKey(epochNum)
    val wrCounterInBytesPadded = view.getAccountStorage(contractAddress, key)
    val wrCounterInBytes = wrCounterInBytesPadded.drop(wrCounterInBytesPadded.length - Ints.BYTES)
    val numOfWithdrawalReqs = Ints.fromByteArray(wrCounterInBytes)

    numOfWithdrawalReqs
  }

  private[horizen] def setWithdrawalEpochCounter(view: BaseAccountStateView, currentEpochNum: Int, nextNumOfWithdrawalReqs: Int): Unit = {
    val nextNumOfWithdrawalReqsBytes = Ints.toByteArray(nextNumOfWithdrawalReqs)
    val paddedNextNumOfWithdrawalReqs = Bytes.concat(new Array[Byte](32 - nextNumOfWithdrawalReqsBytes.length), nextNumOfWithdrawalReqsBytes)
    val wrCounterKey = getWithdrawalEpochCounterKey(currentEpochNum)
    view.updateAccountStorage(contractAddress, wrCounterKey, paddedNextNumOfWithdrawalReqs)
  }


  override private[horizen] def getListOfWithdrawalReqRecords(epochNum: Int, view: BaseAccountStateView): Seq[WithdrawalRequest] = {
    val numOfWithdrawalReqs: Int = getWithdrawalEpochCounter(view, epochNum)

    val listOfWithdrawalReqs = (1 to numOfWithdrawalReqs).map(index => {
      val currentKey = getWithdrawalRequestsKey(epochNum, index)
      WithdrawalRequestSerializer.parseBytes(view.getAccountStorageBytes(contractAddress, currentKey))
    })
    listOfWithdrawalReqs
  }

  protected def execGetListOfWithdrawalReqRecords(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    //TODO should any length between OP_CODE_LENGTH to OP_CODE_LENGTH + 32 be supported?
    if (msg.getData.length != METHOD_CODE_LENGTH + GetListOfWithdrawalRequestsCmdInputDecoder.getABIDataParamsLengthInBytes)
      throw new ExecutionRevertedException(s"Wrong message data field length: ${msg.getData.length}")
    val inputParams = GetListOfWithdrawalRequestsCmdInputDecoder.decode(getArgumentsFromData(msg.getData))
    val listOfWithdrawalReqs = getListOfWithdrawalReqRecords(inputParams.epochNum, view)
    WithdrawalRequestsListEncoder.encode(listOfWithdrawalReqs.asJava)
  }


  private[horizen] def checkWithdrawalRequestValidity(msg: Message): Unit = {
    val withdrawalAmount = msg.getValue

    if (msg.getData.length != METHOD_CODE_LENGTH + AddWithdrawalRequestCmdInputDecoder.getABIDataParamsLengthInBytes) {
      throw new ExecutionRevertedException(s"Wrong message data field length: ${msg.getData.length}")
    } else if (!ZenWeiConverter.isValidZenAmount(withdrawalAmount)) {
      throw new ExecutionRevertedException(s"Withdrawal amount is not a valid Zen amount: $withdrawalAmount")
    } else if (withdrawalAmount.compareTo(DustThresholdInWei) < 0) {
      throw new ExecutionRevertedException(s"Withdrawal amount is under the dust threshold: $withdrawalAmount")
    }
  }

  protected def execAddWithdrawalRequest(msg: Message, view: BaseAccountStateView, currentEpochNum: Int): Array[Byte] = {
    checkWithdrawalRequestValidity(msg)
    val numOfWithdrawalReqs = getWithdrawalEpochCounter(view, currentEpochNum)
    if (numOfWithdrawalReqs >= MaxWithdrawalReqsNumPerEpoch) {
      throw new ExecutionRevertedException("Reached maximum number of Withdrawal Requests per epoch: request is invalid")
    }

    val nextNumOfWithdrawalReqs: Int = numOfWithdrawalReqs + 1
    setWithdrawalEpochCounter(view, currentEpochNum, nextNumOfWithdrawalReqs)

    val inputParams = AddWithdrawalRequestCmdInputDecoder.decode(getArgumentsFromData(msg.getData))
    val withdrawalAmount = msg.getValue
    val request = WithdrawalRequest(inputParams.mcAddr, withdrawalAmount)
    val requestInBytes = request.bytes
    view.updateAccountStorageBytes(contractAddress, getWithdrawalRequestsKey(currentEpochNum, nextNumOfWithdrawalReqs), requestInBytes)

    view.subBalance(msg.getFromAddressBytes, withdrawalAmount)

    val withdrawalEvent = AddWithdrawalRequest(msg.getFrom.get(), request.proposition, withdrawalAmount, currentEpochNum)
    val evmLog = getEvmLog(withdrawalEvent)
    view.addLog(evmLog)

    request.encode
  }

  private[horizen] def calculateKey(keySeed: Array[Byte]): Array[Byte] = {
    Keccak256.hash(keySeed)
  }

  private[horizen] def getWithdrawalEpochCounterKey(withdrawalEpoch: Int): Array[Byte] = {
    calculateKey(Bytes.concat("withdrawalEpochCounter".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpoch)))
  }

  private[horizen] def getWithdrawalRequestsKey(withdrawalEpoch: Int, counter: Int): Array[Byte] = {
    calculateKey(Bytes.concat("withdrawalRequests".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpoch), Ints.toByteArray(counter)))
  }


}

object AddWithdrawalRequestCmdInputDecoder extends ABIDecoder[AddWithdrawalRequestCmdInput] {

  override def getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[Bytes20]() {}))

  override def createType(listOfParams: util.List[Type[_]]): AddWithdrawalRequestCmdInput = {
    AddWithdrawalRequestCmdInput(new MCPublicKeyHashProposition(listOfParams.get(0).asInstanceOf[Bytes20].getValue))
  }

}

case class AddWithdrawalRequestCmdInput(mcAddr: MCPublicKeyHashProposition) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    new StaticStruct(
      new Bytes20(mcAddr.bytes())
    )
  }
}

object GetListOfWithdrawalRequestsCmdInputDecoder extends ABIDecoder[GetListOfWithdrawalRequestsInputCmd] {
  override def getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[Uint32]() {}))

  override def createType(listOfParams: util.List[Type[_]]): GetListOfWithdrawalRequestsInputCmd = {
    GetListOfWithdrawalRequestsInputCmd(listOfParams.get(0).asInstanceOf[Uint32].getValue.intValueExact())
  }

}

case class GetListOfWithdrawalRequestsInputCmd(epochNum: Int) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
   new StaticStruct(
      new Uint32(epochNum)
    )
  }
}

object WithdrawalRequestsListEncoder extends ABIListEncoder[WithdrawalRequest, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]

}
