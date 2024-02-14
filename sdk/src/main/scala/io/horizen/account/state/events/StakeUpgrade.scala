package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.Parameter
import org.web3j.abi.datatypes.generated.Uint32

import scala.annotation.meta.getter

case class StakeUpgrade(
    @(Parameter @getter)(1) oldVersion: Uint32,
    @(Parameter @getter)(2) newVersion: Uint32
)

object StakeUpgrade {
  def apply(oldVersion: Int, newVersion: Int): StakeUpgrade =
    StakeUpgrade(new Uint32(oldVersion), new Uint32(newVersion))
}
