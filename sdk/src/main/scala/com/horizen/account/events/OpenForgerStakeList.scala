package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.account.proposition.AddressProposition
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.{Bytes32, Uint32}

import scala.annotation.meta.getter


case class OpenForgerStakeList(@(Parameter@getter)(1) @(Indexed@getter) from: Address,
                               @(Parameter@getter)(2) @(Indexed@getter) forgerIndex: Uint32,
                               @(Parameter@getter)(3) blockSignProposition: Bytes32)

object OpenForgerStakeList {
  def apply(from: AddressProposition, forgerIndex: Int, blockSignProposition: PublicKey25519Proposition): OpenForgerStakeList = {
    new OpenForgerStakeList(
      new Address(BytesUtils.toHexString(from.address())),
      new Uint32(forgerIndex),
      new Bytes32(blockSignProposition.pubKeyBytes()))
  }
}