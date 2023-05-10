package io.horizen.account.sc2sc

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData}
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder}
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state.events.AddCrossChainMessage
import io.horizen.account.state.{AccountStateView, BaseAccountStateView, ExecutionRevertedException, Message, NativeSmartContractMsgProcessor}
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.evm.Address
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash, CrossChainMessageImpl, CrossChainProtocolVersion}
import io.horizen.utils.ZenCoinsUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.{StaticStruct, Type}
import org.web3j.abi.datatypes.generated.Uint32
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter

trait CrossChainMessageProvider {
  private[horizen] def getCrossChainMessages(epochNum: Int, view: BaseAccountStateView): Seq[CrossChainMessage]
  private[horizen] def getCrossChainMessageHashEpoch(msgHash: CrossChainMessageHash, view: BaseAccountStateView): Option[Int]
}
abstract class AbstractCrossChainMessageProcessor(networkParams: NetworkParams) extends NativeSmartContractMsgProcessor with CrossChainMessageProvider {

  val MaxCrosschainMessagesPerEpoch = CryptoLibProvider.sc2scCircuitFunctions.getMaxCrossChainMessagesPerEpoch
  val DustThresholdInWei: BigInteger = ZenWeiConverter.convertZenniesToWei(ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE))

  protected def execGetListOfCrosschainMessages(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    if (msg.getData.length != METHOD_ID_LENGTH + GetListOfCrosschainMessagesCmdInputDecoder.getABIDataParamsLengthInBytes)
      throw new ExecutionRevertedException(s"Wrong message data field length: ${msg.getData.length}")
    val inputParams = GetListOfCrosschainMessagesCmdInputDecoder.decode(getArgumentsFromData(msg.getData))
    val list = getListOfCrossChainMessagesRecords(inputParams.epochNum, view)
    CrosschainMessagesListEncoder.encode(list.asJava)
  }

  override def getCrossChainMessages(epochNum: Int, view: BaseAccountStateView): Seq[CrossChainMessage] = {
    getListOfCrossChainMessagesRecords(epochNum, view).map(msg => AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount(msg, networkParams))
  }

  private[horizen] def getListOfCrossChainMessagesRecords(epochNum: Int, view: BaseAccountStateView): Seq[AccountCrossChainMessage] = {
    val num: Int = getMessageEpochCounter(view, epochNum)

    val list = (1 to num).map(index => {
      val currentKey = getMessageKey(epochNum, index)
      AccountCrossChainMessageSerializer.parseBytes(view.getAccountStorageBytes(contractAddress, currentKey))
    })
    list
  }


  override private[horizen] def getCrossChainMessageHashEpoch(messageHash: CrossChainMessageHash, view: BaseAccountStateView): Option[Int] = {
    val data = view.getAccountStorage(contractAddress, messageHash.bytes)
    if (data.sameElements(NULL_HEX_STRING_32)) {
      Option.empty
    } else {
      Some(Ints.fromByteArray(data))
    }
  }


  protected def addCrossChahinMessage(messageType: Int,
                                      sender: Address,
                                      receiverSidechain: Array[Byte],
                                      receiver: Array[Byte],
                                      payload: Array[Byte],
                                      view: AccountStateView,
                                      currentEpochNum: Int): Array[Byte] = {
    val numOfReqs = getMessageEpochCounter(view, currentEpochNum)
    if (numOfReqs >= MaxCrosschainMessagesPerEpoch) {
      throw new ExecutionRevertedException("Reached maximum number of CrosschainMessages per epoch: request is invalid")
    }

    val request = AccountCrossChainMessage(messageType, sender.toBytes, receiverSidechain, receiver, payload)
    val messageHash = CryptoLibProvider.sc2scCircuitFunctions.getCrossChainMessageHash(AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount(request, networkParams))

    //check for duplicates in this and other message processor, in any epoch
    if (view.getCrossChainMessageHashEpoch(messageHash).nonEmpty) {
      throw new ExecutionRevertedException("Dupicate crosschain message")
    }

    val nextNum: Int = numOfReqs + 1
    setMessageEpochCounter(view, currentEpochNum, nextNum)


    val requestInBytes = request.bytes
    val messageKey = getMessageKey(currentEpochNum, nextNum)
    view.updateAccountStorageBytes(contractAddress, messageKey, requestInBytes)
    //we store also the mapping [message hash, epochnumber]
    view.updateAccountStorageBytes(contractAddress, messageHash.bytes, Ints.toByteArray(currentEpochNum))

    val event = AddCrossChainMessage(sender, messageType, receiverSidechain, receiver, payload)
    val evmLog = getEthereumConsensusDataLog(event)
    view.addLog(evmLog)

    request.encode
  }

  private[horizen] def getMessageEpochCounter(view: BaseAccountStateView, epochNum: Int) = {
    val key = getMessageEpochCounterKey(epochNum)
    val counterInBytesPadded = view.getAccountStorage(contractAddress, key)
    val counterInBytes = counterInBytesPadded.drop(counterInBytesPadded.length - Ints.BYTES)
    val num = Ints.fromByteArray(counterInBytes)
    num
  }

  private[horizen] def setMessageEpochCounter(view: BaseAccountStateView, currentEpochNum: Int, nextNumOfCrossChainMessages: Int): Unit = {
    val nextNumOfCrossChainMessagesBytes = Ints.toByteArray(nextNumOfCrossChainMessages)
    val paddedNextNumOfCrossChainMessages = Bytes.concat(new Array[Byte](32 - nextNumOfCrossChainMessagesBytes.length), nextNumOfCrossChainMessagesBytes)
    val wrCounterKey = getMessageEpochCounterKey(currentEpochNum)
    view.updateAccountStorage(contractAddress, wrCounterKey, paddedNextNumOfCrossChainMessages)
  }

  private[horizen] def calculateKey(keySeed: Array[Byte]): Array[Byte] = {
    Keccak256.hash(keySeed)
  }

  private[horizen] def getMessageEpochCounterKey(withdrawalEpoch: Int): Array[Byte] = {
    calculateKey(Bytes.concat("crossChainMsgEpochCounter".getBytes, Ints.toByteArray(withdrawalEpoch)))
  }

  private[horizen] def getMessageKey(withdrawalEpoch: Int, counter: Int): Array[Byte] = {
    calculateKey(Bytes.concat("crossChainMsg".getBytes, Ints.toByteArray(withdrawalEpoch), Ints.toByteArray(counter)))
  }
}

object AbstractCrossChainMessageProcessor {
  private[horizen] def buildCrosschainMessageFromAccount(data: AccountCrossChainMessage, params: NetworkParams): CrossChainMessage = {
    new CrossChainMessageImpl(
      CrossChainProtocolVersion.VERSION_1,
      data.messageType,
      params.sidechainId,
      data.sender,
      data.receiverSidechain,
      data.receiver,
      data.payload
    )
  }
}

object GetListOfCrosschainMessagesCmdInputDecoder extends ABIDecoder[GetListOfCrosschainMessagesInputCmd] {
  override def getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[Uint32]() {}))

  override def createType(listOfParams: util.List[Type[_]]): GetListOfCrosschainMessagesInputCmd = {
    GetListOfCrosschainMessagesInputCmd(listOfParams.get(0).asInstanceOf[Uint32].getValue.intValueExact())
  }
}

case class GetListOfCrosschainMessagesInputCmd(epochNum: Int) extends ABIEncodable[StaticStruct] {
  override def asABIType(): StaticStruct = {
    new StaticStruct(
      new Uint32(epochNum)
    )
  }
}

object CrosschainMessagesListEncoder extends ABIListEncoder[AccountCrossChainMessage, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]
}

abstract class CrossChainMessageProcessorConstants {
  val GetListOfCrosschainMessagesCmdSig: String = getABIMethodId("getCrossChainMessages(uint32)")
  val contractAddress: Address
  val contractCode: Array[Byte]
}