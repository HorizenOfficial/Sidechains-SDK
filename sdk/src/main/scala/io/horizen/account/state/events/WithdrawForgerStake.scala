package io.horizen.account.state.events

import com.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
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
