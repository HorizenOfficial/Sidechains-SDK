package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
import org.web3j.abi.datatypes.generated.{Bytes3, Bytes32}
import org.web3j.abi.datatypes.{Address => AbiAddress}

import java.nio.charset.StandardCharsets
import java.util
import scala.annotation.meta.getter

case class AddMcAddrOwnership(
   @(Parameter @getter)(1) @(Indexed @getter) scAddress: AbiAddress,
   @(Parameter @getter)(2) mcAddress_3: Bytes3,
   @(Parameter @getter)(3) mcAddress_32: Bytes32
) {}


object AddMcAddrOwnership {
  def apply(scAddress: Address, mcTransparentAddress: String): AddMcAddrOwnership = {
    val mcAddrBytes = mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
    val mca3 = new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3))
    val mcs32 = new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, 35))
    AddMcAddrOwnership(
      new AbiAddress(scAddress.toString),
      mca3, mcs32
    )
  }
}