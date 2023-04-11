package io.horizen.account.state.events

import io.horizen.account.sc2sc.{AccountCrossChainMessage, AccountCrossChainMessageSerializer}
import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.generated.Bytes32

import scala.annotation.meta.getter

case class AddCrossChainRedeemMessage(
                                       @(Parameter @getter)(1) @(Indexed @getter) accountCrossChainMessage: DynamicBytes,
                                       @(Parameter @getter)(2) certificateDataHash: Bytes32,
                                       @(Parameter @getter)(3) nextCertificateDataHash: Bytes32,
                                       @(Parameter @getter)(4) scCommitmentTreeRoot: Bytes32,
                                       @(Parameter @getter)(5) nextScCommitmentTreeRoot: Bytes32,
                                       @(Parameter @getter)(6) proof: DynamicBytes
                                     )

object AddCrossChainRedeemMessage {
  def apply(
             accountCrossChainMessage: AccountCrossChainMessage,
             certificateDataHash: Array[Byte],
             nextCertificateDataHash: Array[Byte],
             scCommitmentTreeRoot: Array[Byte],
             nextScCommitmentTreeRoot: Array[Byte],
             proof: Array[Byte],
           ): AddCrossChainRedeemMessage =
    new AddCrossChainRedeemMessage(
      new DynamicBytes(AccountCrossChainMessageSerializer.toBytes(accountCrossChainMessage)),
      new Bytes32(certificateDataHash),
      new Bytes32(nextCertificateDataHash),
      new Bytes32(scCommitmentTreeRoot),
      new Bytes32(nextScCommitmentTreeRoot),
      new DynamicBytes(proof)
    )
}