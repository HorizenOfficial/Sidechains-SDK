package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
import org.web3j.abi.datatypes.generated.{Bytes32, Uint256}
import org.web3j.abi.datatypes.{DynamicBytes, Address => AbiAddress}

import java.math.BigInteger
import scala.annotation.meta.getter

case class ProxyInvocation(
    @(Parameter @getter)(1) @(Indexed @getter) from: AbiAddress,
    @(Parameter @getter)(2) @(Indexed @getter) to: AbiAddress,
    @(Parameter @getter)(3) data: DynamicBytes
)


object ProxyInvocation {
  def apply(
             from: Address,
             to: Address,
             data: Array[Byte]
           ): ProxyInvocation = ProxyInvocation(
    new AbiAddress(from.toString),
    new AbiAddress(to.toString),
    new DynamicBytes(data)
  )
}