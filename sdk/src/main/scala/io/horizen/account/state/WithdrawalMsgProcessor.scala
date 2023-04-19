package io.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder, MsgProcessorInputDecoder}
import io.horizen.account.state.events.AddWithdrawalRequest
import io.horizen.account.utils.WellKnownAddresses.WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.proposition.MCPublicKeyHashProposition
import io.horizen.utils.WithdrawalEpochUtils.MaxWithdrawalReqsNumPerEpoch
import io.horizen.utils.ZenCoinsUtils
import io.horizen.evm.Address
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes20, Uint32}
import org.web3j.abi.datatypes.{StaticStruct, Type}
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success, Try}

trait WithdrawalRequestProvider {
  private[horizen] def getListOfWithdrawalReqRecords(epochNum: Int, view: BaseAccountStateView): Seq[WithdrawalRequest]
}

object WithdrawalMsgProcessor extends NativeSmartContractMsgProcessor with WithdrawalRequestProvider {

  override val contractAddress: Address = WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("WithdrawalRequestSmartContractCode")

  val GetListOfWithdrawalReqsCmdSig: String = getABIMethodId("getBackwardTransfers(uint32)")
  val AddNewWithdrawalReqCmdSig: String = getABIMethodId("backwardTransfer(bytes20)")
  val DustThresholdInWei: BigInteger = ZenWeiConverter.convertZenniesToWei(ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE))

  @throws(classOf[ExecutionFailedException])
  override def process(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    val gasView = view.getGasTrackedView(invocation.gasPool)
    getFunctionSignature(invocation.input) match {
      case GetListOfWithdrawalReqsCmdSig =>
        execGetListOfWithdrawalReqRecords(invocation, gasView)

      case AddNewWithdrawalReqCmdSig =>
        execAddWithdrawalRequest(invocation, gasView, context.blockContext.withdrawalEpochNumber)

      case functionSig =>
        throw new ExecutionRevertedException(s"Requested function does not exist. Function signature: $functionSig")
    }
  }

  private def getWithdrawalEpochCounter(view: BaseAccountStateView, epochNum: Int): Int = {
    val key = getWithdrawalEpochCounterKey(epochNum)
    val wrCounterInBytesPadded = view.getAccountStorage(contractAddress, key)
    org.web3j.utils.Numeric.toBigInt(wrCounterInBytesPadded).intValueExact()
  }

  private[horizen] def setWithdrawalEpochCounter(view: BaseAccountStateView, currentEpochNum: Int, nextNumOfWithdrawalReqs: Int): Unit = {
    val paddedNextNumOfWithdrawalReqs = org.web3j.utils.Numeric.toBytesPadded(BigInteger.valueOf(nextNumOfWithdrawalReqs), 32)
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

  protected def execGetListOfWithdrawalReqRecords(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    if (invocation.value.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    if (invocation.input.length != METHOD_ID_LENGTH + GetListOfWithdrawalRequestsCmdInputDecoder.getABIDataParamsLengthInBytes)
      throw new ExecutionRevertedException(s"Wrong message data field length: ${invocation.input.length}")

    val inputParams : GetListOfWithdrawalRequestsCmdInput = Try {
      GetListOfWithdrawalRequestsCmdInputDecoder.decode(getArgumentsFromData(invocation.input))
    } match {
      case Success(decodedBytes) => decodedBytes
      case Failure(ex) =>
        throw new ExecutionRevertedException("Could not decode input params: " + ex.getMessage)
    }

    val listOfWithdrawalReqs = getListOfWithdrawalReqRecords(inputParams.epochNum, view)
    WithdrawalRequestsListEncoder.encode(listOfWithdrawalReqs.asJava)
  }

  private[horizen] def checkWithdrawalRequestValidity(invocation: Invocation): Unit = {
    val withdrawalAmount = invocation.value

    if (invocation.input.length != METHOD_ID_LENGTH + AddWithdrawalRequestCmdInputDecoder.getABIDataParamsLengthInBytes) {
      throw new ExecutionRevertedException(s"Wrong message data field length: ${invocation.input.length}")
    } else if (!ZenWeiConverter.isValidZenAmount(withdrawalAmount)) {
      throw new ExecutionRevertedException(s"Withdrawal amount is not a valid Zen amount: $withdrawalAmount")
    } else if (withdrawalAmount.compareTo(DustThresholdInWei) < 0) {
      throw new ExecutionRevertedException(s"Withdrawal amount is under the dust threshold: $withdrawalAmount")
    }
  }

  protected def execAddWithdrawalRequest(invocation: Invocation, view: BaseAccountStateView, currentEpochNum: Int): Array[Byte] = {
    checkWithdrawalRequestValidity(invocation)
    val numOfWithdrawalReqs = getWithdrawalEpochCounter(view, currentEpochNum)
    if (numOfWithdrawalReqs >= MaxWithdrawalReqsNumPerEpoch) {
      throw new ExecutionRevertedException("Reached maximum number of Withdrawal Requests per epoch: request is invalid")
    }

    val nextNumOfWithdrawalReqs: Int = numOfWithdrawalReqs + 1
    setWithdrawalEpochCounter(view, currentEpochNum, nextNumOfWithdrawalReqs)

    val inputParams = AddWithdrawalRequestCmdInputDecoder.decode(getArgumentsFromData(invocation.input))

    val withdrawalAmount = invocation.value
    val request = WithdrawalRequest(inputParams.mcAddr, withdrawalAmount)
    val requestInBytes = request.bytes
    view.updateAccountStorageBytes(contractAddress, getWithdrawalRequestsKey(currentEpochNum, nextNumOfWithdrawalReqs), requestInBytes)

    view.subBalance(invocation.caller, withdrawalAmount)

    val withdrawalEvent = AddWithdrawalRequest(invocation.caller, request.proposition, withdrawalAmount, currentEpochNum)
    val evmLog = getEthereumConsensusDataLog(withdrawalEvent)
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

object AddWithdrawalRequestCmdInputDecoder
  extends ABIDecoder[AddWithdrawalRequestCmdInput]
    with MsgProcessorInputDecoder[AddWithdrawalRequestCmdInput] {

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

object GetListOfWithdrawalRequestsCmdInputDecoder
  extends ABIDecoder[GetListOfWithdrawalRequestsCmdInput]
    with MsgProcessorInputDecoder[GetListOfWithdrawalRequestsCmdInput] {
  override def getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[Uint32]() {}))

  override def createType(listOfParams: util.List[Type[_]]): GetListOfWithdrawalRequestsCmdInput = {
    GetListOfWithdrawalRequestsCmdInput(listOfParams.get(0).asInstanceOf[Uint32].getValue.intValueExact())
  }

}

case class GetListOfWithdrawalRequestsCmdInput(epochNum: Int) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
   new StaticStruct(
      new Uint32(epochNum)
    )
  }
}

object WithdrawalRequestsListEncoder extends ABIListEncoder[WithdrawalRequest, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]

}
