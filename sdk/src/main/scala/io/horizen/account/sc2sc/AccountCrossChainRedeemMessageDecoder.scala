package io.horizen.account.sc2sc

import io.horizen.account.abi.ABIDecoder
import io.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.generated.{Bytes20, Bytes32, Uint32}
import org.web3j.abi.datatypes.{DynamicBytes, Type, Utf8String}
import org.web3j.abi.{TypeReference, Utils}

import java.nio.charset.StandardCharsets
import java.util

object AccountCrossChainRedeemMessageDecoder extends ABIDecoder[AccountCrossChainRedeemMessage] {
  override def getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    Utils.convert(util.Arrays.asList(
      new TypeReference[Uint32]() {},
      new TypeReference[Bytes20]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes20]() {},
      new TypeReference[Utf8String]() {},

      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Utf8String]() {}
    ))
  }

  override def createType(listOfParams: util.List[Type[_]]): AccountCrossChainRedeemMessage = {
    val messageType = listOfParams.get(0).asInstanceOf[Uint32].getValue.intValue
    val senderSidechain = listOfParams.get(1).asInstanceOf[Bytes32].getValue
    val sender = listOfParams.get(2).asInstanceOf[Bytes20].getValue
    val receiverSidechain = listOfParams.get(3).asInstanceOf[Bytes32].getValue
    val receiver = listOfParams.get(4).asInstanceOf[Bytes20].getValue
    val payloadAsString = listOfParams.get(5).asInstanceOf[Utf8String].getValue
    val payload = payloadAsString.getBytes(StandardCharsets.UTF_8)

    val certificateDataHash = listOfParams.get(6).asInstanceOf[Bytes32].getValue
    val nextCertificateDataHash = listOfParams.get(7).asInstanceOf[Bytes32].getValue
    val scCommitmentTreeRoot = listOfParams.get(8).asInstanceOf[Bytes32].getValue
    val nextScCommitmentTreeRoot = listOfParams.get(9).asInstanceOf[Bytes32].getValue
    val proofAsString = listOfParams.get(10).asInstanceOf[Utf8String].getValue
    val proof = BytesUtils.fromHexString(proofAsString)

    AccountCrossChainRedeemMessage(messageType, senderSidechain, sender, receiverSidechain, receiver, payload, certificateDataHash,
      nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof)
  }
}
