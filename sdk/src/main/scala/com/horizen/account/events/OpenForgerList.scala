package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.evm.utils.Address
import com.horizen.proposition.PublicKey25519Proposition
import org.web3j.abi.datatypes.generated.{Bytes32, Uint32}
import org.web3j.abi.datatypes.{Address => AbiAddress}

import scala.annotation.meta.getter

case class OpenForgerList(
    @(Parameter @getter)(1) @(Indexed @getter) forgerIndex: Uint32,
    @(Parameter @getter)(2) from: AbiAddress,
    @(Parameter @getter)(3) blockSignProposition: Bytes32
)

object OpenForgerList {
  def apply(
      forgerIndex: Int,
      from: Address,
      blockSignProposition: PublicKey25519Proposition
  ): OpenForgerList = OpenForgerList(
    new Uint32(forgerIndex),
    new AbiAddress(from.toString),
    new Bytes32(blockSignProposition.pubKeyBytes())
  )
}
