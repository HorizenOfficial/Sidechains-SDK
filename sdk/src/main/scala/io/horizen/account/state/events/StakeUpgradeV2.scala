package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.Parameter
import org.web3j.abi.datatypes.generated.{Int32, Uint32}

import scala.annotation.meta.getter

case class StakeUpgradeV2(
    @(Parameter @getter)(1) oldVersion: Int32,
    @(Parameter @getter)(2) newVersion: Int32
)

object StakeUpgradeV2 {
  def apply(oldVersion: Int, newVersion: Int): StakeUpgradeV2 =
    StakeUpgradeV2(new Int32(oldVersion), new Int32(newVersion))
}
