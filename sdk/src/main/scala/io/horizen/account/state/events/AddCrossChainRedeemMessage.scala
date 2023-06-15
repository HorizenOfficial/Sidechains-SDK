package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
import org.web3j.abi.datatypes.generated.{Bytes20, Bytes32, Bytes4, Uint32}
import org.web3j.abi.datatypes.{Address => AbiAddress}

import scala.annotation.meta.getter

case class AddCrossChainRedeemMessage(
                                       @(Parameter@getter)(1) @(Indexed@getter) sender: AbiAddress,
                                       @(Parameter@getter)(2) @(Indexed@getter) messageType: Uint32,
                                       @(Parameter@getter)(3) receiverSidechain: Bytes32,
                                       @(Parameter@getter)(4) receiver: Bytes20,
                                       @(Parameter@getter)(5) payload: Bytes4,
                                       @(Parameter @getter)(6) certificateDataHash: Bytes32,
                                       @(Parameter @getter)(7) nextCertificateDataHash: Bytes32,
                                       @(Parameter @getter)(8) scCommitmentTreeRoot: Bytes32,
                                       @(Parameter @getter)(9) nextScCommitmentTreeRoot: Bytes32
                                     )

object AddCrossChainRedeemMessage {
  def apply(
             sender: Address,
             messageType: Int,
             receiverSidechain: Array[Byte],
             receiver: Array[Byte],
             payload: Array[Byte],
             certificateDataHash: Array[Byte],
             nextCertificateDataHash: Array[Byte],
             scCommitmentTreeRoot: Array[Byte],
             nextScCommitmentTreeRoot: Array[Byte]
           ): AddCrossChainRedeemMessage =
    new AddCrossChainRedeemMessage(
      new AbiAddress(sender.toString),
      new Uint32(messageType),
      new Bytes32(receiverSidechain),
      new Bytes20(receiver),
      new Bytes4(payload),
      new Bytes32(certificateDataHash),
      new Bytes32(nextCertificateDataHash),
      new Bytes32(scCommitmentTreeRoot),
      new Bytes32(nextScCommitmentTreeRoot)
    )
}