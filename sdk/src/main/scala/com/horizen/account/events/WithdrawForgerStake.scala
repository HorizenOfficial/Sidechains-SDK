package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.evm.utils.Address
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.{Address => AbiAddress}

import scala.annotation.meta.getter

case class WithdrawForgerStake(
    @(Parameter @getter)(1) @(Indexed @getter) owner: AbiAddress,
    @(Parameter @getter)(2) stakeId: Bytes32
)

object WithdrawForgerStake {
  def apply(owner: Address, stakeId: Array[Byte]): WithdrawForgerStake =
    WithdrawForgerStake(new AbiAddress(owner.toString), new Bytes32(stakeId))
}
