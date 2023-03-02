package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.account.sc2sc.{AccountCrossChainMessage, AccountCrossChainMessageSerializer}
import org.web3j.abi.datatypes.DynamicBytes

import scala.annotation.meta.getter

case class AddCrossChainRedeemMessage(
                                       @(Parameter @getter)(1) @(Indexed @getter) accountCrossChainMessage: DynamicBytes,
                                       @(Parameter @getter)(2) certificateDataHash: DynamicBytes,
                                       @(Parameter @getter)(3) nextCertificateDataHash: DynamicBytes,
                                       @(Parameter @getter)(4) scCommitmentTreeRoot: DynamicBytes,
                                       @(Parameter @getter)(5) nextScCommitmentTreeRoot: DynamicBytes,
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
      new DynamicBytes(certificateDataHash),
      new DynamicBytes(nextCertificateDataHash),
      new DynamicBytes(scCommitmentTreeRoot),
      new DynamicBytes(nextScCommitmentTreeRoot),
      new DynamicBytes(proof)
    )
}