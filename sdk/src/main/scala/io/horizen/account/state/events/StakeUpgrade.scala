package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
import org.web3j.abi.datatypes.generated.{Bytes32, Uint32}
import org.web3j.abi.datatypes.{Address => AbiAddress}

import scala.annotation.meta.getter

case class StakeUpgrade(
    @(Parameter @getter)(1) oldVersion: Uint32,
    @(Parameter @getter)(2) newVersion: Uint32
)

object StakeUpgrade {
  def apply(oldVersion: Int, newVersion: Int): StakeUpgrade =
    StakeUpgrade(new Uint32(oldVersion), new Uint32(newVersion))
}
