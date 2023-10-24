package io.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, MsgProcessorInputDecoder}
import io.horizen.account.state.CertificateKeyRotationMsgProcessor.{CertificateKeyRotationContractAddress, CertificateKeyRotationContractCode, SubmitKeyRotationReqCmdSig}
import io.horizen.account.state.events.SubmitKeyRotation
import io.horizen.account.utils.WellKnownAddresses.CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS
import io.horizen.certificatesubmitter.keys.KeyRotationProofTypes.{KeyRotationProofType, MasterKeyRotationProofType, SigningKeyRotationProofType}
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, KeyRotationProofSerializer, KeyRotationProofTypes}
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.params.NetworkParams
import io.horizen.proof.SchnorrProof
import io.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import io.horizen.evm.Address
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes32, Uint32}
import org.web3j.abi.datatypes.{StaticStruct, Type}
import sparkz.crypto.hash.{Digest32, Keccak256}
import sparkz.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

import java.nio.charset.StandardCharsets
import java.util
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

trait CertificateKeysProvider {
  private[horizen] def getKeyRotationProof(epochNum: Int, index: Int, keyType: KeyRotationProofType,  view: BaseAccountStateView): Option[KeyRotationProof]

  private[horizen] def getCertifiersKeys(epochNum: Int, view: BaseAccountStateView): CertifiersKeys
}

case class CertificateKeyRotationMsgProcessor(params: NetworkParams) extends NativeSmartContractMsgProcessor with CertificateKeysProvider {

  override val contractAddress: Address = CertificateKeyRotationContractAddress
  override val contractCode: Array[Byte] = CertificateKeyRotationContractCode

  @throws(classOf[ExecutionFailedException])
  override def process(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    val gasView = view.getGasTrackedView(invocation.gasPool)
    getFunctionSignature(invocation.input) match {
      case SubmitKeyRotationReqCmdSig =>
        execSubmitKeyRotation(invocation, gasView, context.blockContext.withdrawalEpochNumber)

      case functionSig =>
        throw new ExecutionRevertedException(s"Requested function does not exist. Function signature: $functionSig")
    }
  }

  override private[horizen] def getCertifiersKeys(epochNum: Int, view: BaseAccountStateView) = {
    val singingKeys = params.signersPublicKeys.zipWithIndex
      .map(key_index => getLatestSigningKey(view, key_index._1, epochNum + 1, key_index._2))
      .toVector

    val masterKeys = params.mastersPublicKeys.zipWithIndex
      .map(key_index => getLatestMasterKey(view, key_index._1, epochNum + 1, key_index._2))
      .toVector

    CertifiersKeys(singingKeys, masterKeys)
  }

  private def getLatestMasterKey(view: BaseAccountStateView, configKey: SchnorrProposition, requestedEpoch: Int, index: Int): SchnorrProposition = {
    val masterKeyChangeHistory = getKeysRotationHistory(MasterKeyRotationProofType, index, view)

    masterKeyChangeHistory.epochNumbers.find(_ < requestedEpoch)
      .flatMap(getMasterKey(_, index, view))
      .getOrElse(configKey)
  }

  private def getLatestSigningKey(view: BaseAccountStateView, configKey: SchnorrProposition, requestedEpoch: Int, index: Int): SchnorrProposition = {
    val signingKeyChangeHistory = getKeysRotationHistory(SigningKeyRotationProofType, index, view)

    signingKeyChangeHistory.epochNumbers.find(_ < requestedEpoch)
      .flatMap(getSigningKey(_, index, view))
      .getOrElse(configKey)
  }

  override private[horizen] def getKeyRotationProof(epochNum: Int, index: Int, keyType: KeyRotationProofType, view: BaseAccountStateView): Option[KeyRotationProof] = {
    val key = getKeyRotationProofKey(keyType, epochNum, index)
    val maybeData = view.getAccountStorageBytes(contractAddress, key)
    if (maybeData.length > 0)
      Some(KeyRotationProofSerializer.parseBytes(maybeData))
    else
      None
  }

  private[horizen] def getKeysRotationHistory(keyType: KeyRotationProofType, index: Int, view: BaseAccountStateView): KeyRotationHistory = {
    val maybeData = view.getAccountStorageBytes(contractAddress, getKeysRotationHistoryKey(keyType, index))
    if (maybeData.length > 0)
      KeyRotationHistorySerializer.parseBytes(maybeData)
    else
      KeyRotationHistory(List())
  }

  private def putKeyRotationHistory(keyType: KeyRotationProofType, index: Int, view: BaseAccountStateView, history: KeyRotationHistory): Unit = {
    view.updateAccountStorageBytes(contractAddress, getKeysRotationHistoryKey(keyType, index), history.bytes)
  }

  private def putKeyRotationProof(epochNum: Int, view: BaseAccountStateView, keyRotationProof: KeyRotationProof): Unit = {
    val key = getKeyRotationProofKey(keyRotationProof.keyType, epochNum, keyRotationProof.index)
    view.updateAccountStorageBytes(contractAddress, key, keyRotationProof.bytes)
  }

  private def getSigningKey(epochNumber: Int, index: Int, view: BaseAccountStateView): Option[SchnorrProposition] = {
    val bytes = view.getAccountStorageBytes(contractAddress, getSigningKeyKey(epochNumber, index))
    if (bytes.length > 0)
      Some(SchnorrPropositionSerializer.getSerializer.parseBytes(bytes))
    else
      None
  }

  private def getMasterKey(epochNumber: Int, index: Int, view: BaseAccountStateView): Option[SchnorrProposition] = {
    val bytes = view.getAccountStorageBytes(contractAddress, getMasterKeyKey(epochNumber, index))
    if (bytes.length > 0)
      Some(SchnorrPropositionSerializer.getSerializer.parseBytes(bytes))
    else
      None
  }

  private def checkKeyRotationProofValidity(keyRotationProof: KeyRotationProof, newKeySignature: SchnorrProof, currentEpochNum: Int, view: BaseAccountStateView): Try[Unit] = Try {
    val index = keyRotationProof.index
    if (index < 0 || index >= params.signersPublicKeys.length)
      throw new ExecutionRevertedException(s"Key rotation proof - key index out for range: $index")

    val signingKeyFromConfig = params.signersPublicKeys(index)
    val masterKeyFromConfig = params.mastersPublicKeys(index)

    val newKeyAsMessage = keyRotationProof.keyType match {
      case SigningKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForSigningKeyUpdate(keyRotationProof.newKey.pubKeyBytes(), currentEpochNum, params.sidechainId)
      case MasterKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForMasterKeyUpdate(keyRotationProof.newKey.pubKeyBytes(), currentEpochNum, params.sidechainId)
    }

    val latestSigningKey = getLatestSigningKey(view, signingKeyFromConfig, currentEpochNum, index)
    if (!keyRotationProof.signingKeySignature.isValid(latestSigningKey, newKeyAsMessage))
      throw new ExecutionRevertedException(s"Key rotation proof - signing signature is invalid: $index")

    val latestMasterKey = getLatestMasterKey(view, masterKeyFromConfig, currentEpochNum, index)
    if (!keyRotationProof.masterKeySignature.isValid(latestMasterKey, newKeyAsMessage))
      throw new ExecutionRevertedException(s"Key rotation proof - master signature is invalid: $index")

    if (!newKeySignature.isValid(keyRotationProof.newKey, newKeyAsMessage))
      throw new ExecutionRevertedException(s"Key rotation proof - self signature is invalid: $index")
  }

  private def execSubmitKeyRotation(invocation: Invocation, view: BaseAccountStateView, currentEpochNum: Int): Array[Byte] = {
    //verify
    checkInvocationValidity(invocation)

    val inputData = SubmitKeyRotationCmdInputDecoder.decode(getArgumentsFromData(invocation.input))
    val keyRotationProof = inputData.keyRotationProof
    val keyIndex = keyRotationProof.index
    val keyType = keyRotationProof.keyType
    checkKeyRotationProofValidity(keyRotationProof, inputData.newKeySignature, currentEpochNum, view) match {
      case Success(_) =>
      case Failure(ex) =>
        throw new ExecutionRevertedException("Key Rotation Proof is invalid: " + ex.getMessage)
    }

    //save proof
    putKeyRotationProof(currentEpochNum, view, keyRotationProof)

    //save new key
    val storageKey = keyType match {
      case SigningKeyRotationProofType => getSigningKeyKey(currentEpochNum, keyIndex)
      case MasterKeyRotationProofType => getMasterKeyKey(currentEpochNum, keyIndex)
    }
    view.updateAccountStorageBytes(contractAddress, storageKey, keyRotationProof.newKey.bytes())

    //update history
    val history = getKeysRotationHistory(keyType, keyIndex, view)
    if (!history.epochNumbers.headOption.exists(_.equals(currentEpochNum)))
      putKeyRotationHistory(keyType, keyIndex, view, KeyRotationHistory(currentEpochNum :: history.epochNumbers))

    //publish event
    val keyRotationEvent = SubmitKeyRotation(keyType, keyIndex, keyRotationProof.newKey, currentEpochNum)
    val evmLog = getEthereumConsensusDataLog(keyRotationEvent)
    view.addLog(evmLog)

    keyRotationProof.encode()
  }

  private def checkInvocationValidity(invocation: Invocation): Unit = {
    if (invocation.input.length != METHOD_ID_LENGTH + SubmitKeyRotationCmdInputDecoder.getABIDataParamsStaticLengthInBytes) {
      throw new ExecutionRevertedException(s"Wrong invocation data field length: ${invocation.input.length}")
    } else if (invocation.value.signum() != 0) {
      throw new ExecutionRevertedException(s"Value is non-zero: $invocation")
    }
  }

  private def calculateKey(keySeed: Array[Byte]): Array[Byte] = {
    Keccak256.hash(keySeed)
  }

  private def getKeyRotationProofKey(keyType: KeyRotationProofType, withdrawalEpoch: Int, index: Int): Array[Byte] = {
    calculateKey(Bytes.concat(
      "keyRotationProof".getBytes(StandardCharsets.UTF_8),
      Ints.toByteArray(keyType.id),
      Ints.toByteArray(withdrawalEpoch),
      Ints.toByteArray(index)
    ))
  }

  private def getSigningKeyKey(epoch: Int, index: Int): Array[Byte] = {
    calculateKey(Bytes.concat("signingKey".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(epoch), Ints.toByteArray(index)))
  }

  private def getMasterKeyKey(epoch: Int, index: Int): Array[Byte] = {
    calculateKey(Bytes.concat("masterKey".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(epoch), Ints.toByteArray(index)))
  }

  private def getKeysRotationHistoryKey(keyType: KeyRotationProofType, index: Int): Array[Byte] = {
    calculateKey(Bytes.concat("keyHistory".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(keyType.id), Ints.toByteArray(index)))
  }
}

object CertificateKeyRotationMsgProcessor {
  val CertificateKeyRotationContractAddress: Address = CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS
  val CertificateKeyRotationContractCode: Digest32 = Keccak256.hash("KeyRotationSmartContractCode")

  val SubmitKeyRotationReqCmdSig: String = getABIMethodId("submitKeyRotation(uint32,uint32,bytes32,bytes1,bytes32,bytes32,bytes32,bytes32,bytes32,bytes32)")
}

case class SubmitKeyRotationCmdInput(keyRotationProof: KeyRotationProof, newKeySignature: SchnorrProof) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val listOfParams: util.List[Type[_]] = new util.ArrayList(
      keyRotationProof.asABIType().getValue.asInstanceOf[util.List[Type[_]]],
    )
    listOfParams.add(new Bytes32(newKeySignature.bytes().take(32)))
    listOfParams.add(new Bytes32(newKeySignature.bytes().drop(32)))
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(keyRotationProof: %s)"
    .format(this.getClass.toString, keyRotationProof)
}

object SubmitKeyRotationCmdInputDecoder
  extends ABIDecoder[SubmitKeyRotationCmdInput]
    with MsgProcessorInputDecoder[SubmitKeyRotationCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Uint32]() {},
      new TypeReference[Uint32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes1]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))

  override def createType(listOfParams: util.List[Type[_]]): SubmitKeyRotationCmdInput = {
    val keyType = KeyRotationProofTypes(listOfParams.get(0).asInstanceOf[Uint32].getValue.intValue())
    val index = listOfParams.get(1).asInstanceOf[Uint32].getValue.intValue()
    val newKey = new SchnorrProposition(listOfParams.get(2).asInstanceOf[Bytes32].getValue ++ listOfParams.get(3).asInstanceOf[Bytes1].getValue)
    val signingSignature = new SchnorrProof(listOfParams.get(4).asInstanceOf[Bytes32].getValue ++ listOfParams.get(5).asInstanceOf[Bytes32].getValue)
    val masterSignature = new SchnorrProof(listOfParams.get(6).asInstanceOf[Bytes32].getValue ++ listOfParams.get(7).asInstanceOf[Bytes32].getValue)
    val newKeySignature = new SchnorrProof(listOfParams.get(8).asInstanceOf[Bytes32].getValue ++ listOfParams.get(9).asInstanceOf[Bytes32].getValue)
    val keyRotationProof = KeyRotationProof(
      keyType = keyType,
      index = index,
      newKey = newKey,
      signingKeySignature = signingSignature,
      masterKeySignature = masterSignature
    )
    SubmitKeyRotationCmdInput(keyRotationProof, newKeySignature)
  }

}

case class KeyRotationHistory(epochNumbers: List[Int]) extends BytesSerializable {
  override type M = KeyRotationHistory

  override def serializer: SparkzSerializer[KeyRotationHistory] = KeyRotationHistorySerializer
}

object KeyRotationHistorySerializer extends SparkzSerializer[KeyRotationHistory] {
  override def serialize(obj: KeyRotationHistory, w: Writer): Unit = {
    w.putUInt(obj.epochNumbers.size)
    obj.epochNumbers.foreach { epoch =>
      w.putUInt(epoch)
    }
  }

  override def parse(r: Reader): KeyRotationHistory = {
    val size = r.getUInt().toInt
    val buffer = mutable.ListBuffer[Int]()
    for (_ <- 0 until size) {
      buffer += r.getUInt().toInt
    }
    KeyRotationHistory(buffer.toList)
  }
}
