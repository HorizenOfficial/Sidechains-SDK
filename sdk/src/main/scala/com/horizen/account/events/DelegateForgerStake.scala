package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.account.proposition.AddressProposition
import com.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.{Bytes32, Uint256}

import java.math.BigInteger
import scala.annotation.meta.getter

case class DelegateForgerStake(
    @(Parameter @getter)(1) @(Indexed @getter) from: Address,
    @(Parameter @getter)(2) @(Indexed @getter) owner: Address,
    @(Parameter @getter)(3) stakeId: Bytes32,
    @(Parameter @getter)(4) value: Uint256
)

object DelegateForgerStake {
  def apply(
      fromAddress: AddressProposition,
      owner: AddressProposition,
      stakeId: Array[Byte],
      value: BigInteger
  ): DelegateForgerStake = DelegateForgerStake(
    new Address(BytesUtils.toHexString(fromAddress.address())),
    new Address(BytesUtils.toHexString(owner.address())),
    new Bytes32(stakeId),
    new Uint256(value)
  )
}
