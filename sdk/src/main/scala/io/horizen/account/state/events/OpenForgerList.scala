package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.proposition.PublicKey25519Proposition
import io.horizen.evm.Address
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
