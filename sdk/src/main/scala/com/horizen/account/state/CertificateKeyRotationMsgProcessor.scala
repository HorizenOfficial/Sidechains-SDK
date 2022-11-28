package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.abi.ABIUtil.{METHOD_CODE_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import com.horizen.account.abi.{ABIDecoder, ABIEncodable}
import com.horizen.account.events.SubmitKeyRotation
import com.horizen.account.state.CertificateKeyRotationMsgProcessor.{CertificateKeyRotationContractAddress, CertificateKeyRotationContractCode, GasSpentForSubmitKeyRotationCmd, SubmitKeyRotationReqCmdSig}
import com.horizen.account.state.KeyRotationProofType.{MasterKeyRotationProofType, SigningKeyRotationProofType}
import com.horizen.params.NetworkParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import com.horizen.utils.BytesUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes32, Uint32}
import org.web3j.abi.datatypes.{StaticStruct, Type}
import scorex.crypto.hash.{Digest32, Keccak256}
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

import java.math.BigInteger
import java.util
import scala.collection.mutable
import scala.util.Try

trait CertificateKeysProvider {
  private[horizen] def getKeyRotationProof(keyType: KeyRotationProofType.Value, epochNum: Int, index: Int, view: BaseAccountStateView): Option[KeyRotationProof]

  private[horizen] def getCertifiersKeys(epochNum: Int, view: BaseAccountStateView): CertifiersKeys
}

case class CertificateKeyRotationMsgProcessor(params: NetworkParams) extends FakeSmartContractMsgProcessor with CertificateKeysProvider {

  override val contractAddress: Array[Byte] = CertificateKeyRotationContractAddress
  override val contractCode: Array[Byte] = CertificateKeyRotationContractCode

  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    //TODO: check errors in Ethereum, maybe for some kind of errors there a predefined types or codes
    val gasView = new AccountStateViewGasTracked(view, gas)
    getFunctionSignature(msg.getData) match {
      case SubmitKeyRotationReqCmdSig =>
        gas.subGas(GasSpentForSubmitKeyRotationCmd)
        execSubmitKeyRotation(msg, gasView, blockContext.withdrawalEpochNumber)

      case functionSig =>
        throw new ExecutionRevertedException(s"Requested function does not exist. Function signature: $functionSig")
    }
  }

  override private[horizen] def getCertifiersKeys(epochNum: Int, view: BaseAccountStateView) = {
    val singingKeys = params.signersPublicKeys.zipWithIndex
      .map(key_index => getLatestSigningKey(view, key_index._1, epochNum, key_index._2))
      .toVector

    val masterKeys = params.masterPublicKeys.zipWithIndex
      .map(key_index => getLatestMasterKey(view, key_index._1, epochNum, key_index._2))
      .toVector

    CertifiersKeys(singingKeys, masterKeys)
  }

  private def getLatestMasterKey(view: BaseAccountStateView, configKey: SchnorrProposition, epoch: Int, index: Int): SchnorrProposition = {
    val masterKeyChangeHistory = getKeysRotationHistory(MasterKeyRotationProofType, index, view)

    masterKeyChangeHistory.epochNumbers.find(epoch > _)
      .flatMap(getMasterKey(_, view))
      .getOrElse(configKey)
  }

  private def getLatestSigningKey(view: BaseAccountStateView, configKey: SchnorrProposition, epoch: Int, index: Int): SchnorrProposition = {
    val signingKeyChangeHistory = getKeysRotationHistory(SigningKeyRotationProofType, index, view)

    signingKeyChangeHistory.epochNumbers.find(epoch > _)
      .flatMap(getSigningKey(_, view))
      .getOrElse(configKey)
  }

  override private[horizen] def getKeyRotationProof(keyType: KeyRotationProofType.Value, epochNum: Int, index: Int, view: BaseAccountStateView): Option[KeyRotationProof] = {
    val key = getKeyRotationProofKey(keyType, epochNum, index)
    val maybeData = view.getAccountStorageBytes(contractAddress, key)
    if (maybeData.length > 0)
      Some(KeyRotationProofSerializer.parseBytes(maybeData))
    else
      None
  }

  private[horizen] def getKeysRotationHistory(keyType: KeyRotationProofType.Value, index: Int, view: BaseAccountStateView): KeyRotationHistory = {
    Try(
      KeyRotationHistorySerializer.parseBytes(view.getAccountStorageBytes(contractAddress, getKeysRotationHistoryKey(keyType, index)))
    )
      .getOrElse(KeyRotationHistory(List()))
  }

  private def putKeyRotationHistory(keyType: KeyRotationProofType.Value, index: Int, view: BaseAccountStateView, history: KeyRotationHistory): Unit = {
    view.updateAccountStorageBytes(contractAddress, getKeysRotationHistoryKey(keyType, index), history.bytes)
  }

  private def putKeyRotationProof(epochNum: Int, view: BaseAccountStateView, keyRotationProof: KeyRotationProof): Unit = {
    val key = getKeyRotationProofKey(keyRotationProof.keyType, epochNum, keyRotationProof.index)
    view.updateAccountStorageBytes(contractAddress, key, keyRotationProof.bytes)
  }

  private def getSigningKey(index: Int, view: BaseAccountStateView): Option[SchnorrProposition] = {
    val bytes = view.getAccountStorageBytes(contractAddress, getSigningKeyKey(index))
    if (bytes.length > 0)
      Some(SchnorrPropositionSerializer.getSerializer.parseBytes(bytes))
    else
      None
  }

  private def getMasterKey(index: Int, view: BaseAccountStateView): Option[SchnorrProposition] = {
    val bytes = view.getAccountStorageBytes(contractAddress, getMasterKeyKey(index))
    if (bytes.length > 0)
      Some(SchnorrPropositionSerializer.getSerializer.parseBytes(bytes))
    else
      None
  }

  private def checkKeyRotationProofValidity(keyRotationProof: KeyRotationProof, newKeySignature: SchnorrProof, currentEpochNum: Int, view: BaseAccountStateView): Unit = {
    val index = keyRotationProof.index
    if (index < 0 || index >= params.signersPublicKeys.length)
      throw new ExecutionRevertedException(s"Key rotation proof - key index out for range: $index")

    val singingKeyFromConfig = params.signersPublicKeys(index)
    val masterKeyFromConfig = params.masterPublicKeys(index)

    val latestSigningKey = getLatestSigningKey(view, singingKeyFromConfig, currentEpochNum, index)
    val latestMasterKey = getLatestMasterKey(view, masterKeyFromConfig, currentEpochNum, index)
    if (!keyRotationProof.signingKeySignature.isValid(latestSigningKey, keyRotationProof.newValueOfKey.bytes().take(32)))
      throw new ExecutionRevertedException(s"Key rotation proof - signing signature is invalid: $index")

    if (!keyRotationProof.masterKeySignature.isValid(latestMasterKey, keyRotationProof.newValueOfKey.bytes().take(32)))
      throw new ExecutionRevertedException(s"Key rotation proof - master signature is invalid: $index")

    if (!newKeySignature.isValid(keyRotationProof.newValueOfKey, keyRotationProof.newValueOfKey.bytes().take(32)))
      throw new ExecutionRevertedException(s"Key rotation proof - self signature is invalid: $index")
  }

  private def execSubmitKeyRotation(msg: Message, view: BaseAccountStateView, currentEpochNum: Int): Array[Byte] = {
    //verify
    checkMessageValidity(msg)
    val inputData = SubmitKeyRotationCmdInputDecoder.decode(getArgumentsFromData(msg.getData))
    val keyRotationProof = inputData.keyRotationProof
    val keyIndex = keyRotationProof.index
    val keyType = keyRotationProof.keyType
    checkKeyRotationProofValidity(keyRotationProof, inputData.newKeySignature, currentEpochNum, view)

    //save proof
    putKeyRotationProof(currentEpochNum, view, keyRotationProof)

    //save new key
    val storageKey = keyType match {
      case SigningKeyRotationProofType => getSigningKeyKey(keyIndex)
      case MasterKeyRotationProofType => getMasterKeyKey(keyIndex)
    }
    view.updateAccountStorageBytes(contractAddress, storageKey, keyRotationProof.newValueOfKey.bytes())

    //update history
    val history = getKeysRotationHistory(keyType, keyIndex, view)
    if (!history.epochNumbers.headOption.exists(_.equals(currentEpochNum)))
      putKeyRotationHistory(keyType, keyIndex, view, KeyRotationHistory(currentEpochNum :: history.epochNumbers))

    //publish event
    val withdrawalEvent = SubmitKeyRotation(keyType, keyIndex, keyRotationProof.newValueOfKey, currentEpochNum)
    val evmLog = getEvmLog(withdrawalEvent)
    view.addLog(evmLog)

    keyRotationProof.bytes
  }

  private def checkMessageValidity(msg: Message): Unit = {
    val msgValue = msg.getValue

    if (msg.getData.length != METHOD_CODE_LENGTH + SubmitKeyRotationCmdInputDecoder.getABIDataParamsLengthInBytes) {
      throw new ExecutionRevertedException(s"Wrong message data field length: ${msg.getData.length}")
    } else if (!msgValue.equals(BigInteger.ZERO)) {
      throw new ExecutionRevertedException(s"SubmitKeyRotation message value is non-zero: $msg")
    }
  }

  private def calculateKey(keySeed: Array[Byte]): Array[Byte] = {
    Keccak256.hash(keySeed)
  }

  private def getKeyRotationProofKey(keyType: KeyRotationProofType.Value, withdrawalEpoch: Int, index: Int): Array[Byte] = {
    calculateKey(Bytes.concat(
      "keyRotationProof".getBytes,
      Ints.toByteArray(keyType.id),
      Ints.toByteArray(withdrawalEpoch),
      Ints.toByteArray(index)
    ))
  }

  private def getSigningKeyKey(index: Int): Array[Byte] = {
    calculateKey(Bytes.concat("signingKey".getBytes, Ints.toByteArray(index)))
  }

  private def getMasterKeyKey(index: Int): Array[Byte] = {
    calculateKey(Bytes.concat("masterKey".getBytes, Ints.toByteArray(index)))
  }

  private def getKeysRotationHistoryKey(keyType: KeyRotationProofType.Value, index: Int): Array[Byte] = {
    calculateKey(Bytes.concat("keyHistory".getBytes, Ints.toByteArray(keyType.id), Ints.toByteArray(index)))
  }
}

object CertificateKeyRotationMsgProcessor {
  val CertificateKeyRotationContractAddress: Array[Byte] = BytesUtils.fromHexString("0000000000000000000044444444444444444444")
  val CertificateKeyRotationContractCode: Digest32 = Keccak256.hash("KeyRotationSmartContractCode")

  val SubmitKeyRotationReqCmdSig: String = getABIMethodId("submitKeyRotation(uint32,uint32,bytes1,bytes32,bytes32,bytes32,bytes32,bytes32,bytes32,bytes32)")

  //TODO Define a proper amount of gas spent for each operation
  val GasSpentForSubmitKeyRotationCmd: BigInteger = BigInteger.ONE
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

object SubmitKeyRotationCmdInputDecoder extends ABIDecoder[SubmitKeyRotationCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Uint32]() {},
      new TypeReference[Uint32]() {},
      new TypeReference[Bytes1]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))

  override def createType(listOfParams: util.List[Type[_]]): SubmitKeyRotationCmdInput = {
    val keyType = KeyRotationProofType(listOfParams.get(0).asInstanceOf[Uint32].getValue.intValue())
    val index = listOfParams.get(1).asInstanceOf[Uint32].getValue.intValue()
    val newKey = new SchnorrProposition(listOfParams.get(2).asInstanceOf[Bytes1].getValue ++ listOfParams.get(3).asInstanceOf[Bytes32].getValue)
    val signingSignature = new SchnorrProof(listOfParams.get(4).asInstanceOf[Bytes32].getValue ++ listOfParams.get(5).asInstanceOf[Bytes32].getValue)
    val masterSignature = new SchnorrProof(listOfParams.get(6).asInstanceOf[Bytes32].getValue ++ listOfParams.get(7).asInstanceOf[Bytes32].getValue)
    val newKeySignature = new SchnorrProof(listOfParams.get(8).asInstanceOf[Bytes32].getValue ++ listOfParams.get(9).asInstanceOf[Bytes32].getValue)
    val keyRotationProof = KeyRotationProof(
      keyType = keyType,
      index = index,
      newValueOfKey = newKey,
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

object Test extends App {

}