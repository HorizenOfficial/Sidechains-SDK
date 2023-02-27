package com.horizen.account.state.events

import com.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
import org.web3j.abi.datatypes.generated.{Bytes32, Uint256}
import org.web3j.abi.datatypes.{Address => AbiAddress}

import java.math.BigInteger
import scala.annotation.meta.getter

case class DelegateForgerStake(
    @(Parameter @getter)(1) @(Indexed @getter) from: AbiAddress,
    @(Parameter @getter)(2) @(Indexed @getter) owner: AbiAddress,
    @(Parameter @getter)(3) stakeId: Bytes32,
    @(Parameter @getter)(4) value: Uint256
)

object DelegateForgerStake {
  def apply(
      from: Address,
      owner: Address,
      stakeId: Array[Byte],
      value: BigInteger
  ): DelegateForgerStake = DelegateForgerStake(
    new AbiAddress(from.toString),
    new AbiAddress(owner.toString),
    new Bytes32(stakeId),
    new Uint256(value)
  )
}
