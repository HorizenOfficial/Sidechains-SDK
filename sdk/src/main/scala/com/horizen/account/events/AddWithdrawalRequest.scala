package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.evm.Address
import com.horizen.proposition.MCPublicKeyHashProposition
import org.web3j.abi.datatypes.generated.{Bytes20, Uint256, Uint32}
import org.web3j.abi.datatypes.{Address => AbiAddress}

import java.math.BigInteger
import scala.annotation.meta.getter

case class AddWithdrawalRequest(
    @(Parameter @getter)(1) @(Indexed @getter) from: AbiAddress,
    @(Parameter @getter)(2) @(Indexed @getter) mcDest: Bytes20,
    @(Parameter @getter)(3) value: Uint256,
    @(Parameter @getter)(4) epochNumber: Uint32
) {}

object AddWithdrawalRequest {
  def apply(
      from: Address,
      mcDest: MCPublicKeyHashProposition,
      value: BigInteger,
      epochNumber: Int
  ): AddWithdrawalRequest = AddWithdrawalRequest(
    new AbiAddress(from.toString),
    new Bytes20(mcDest.bytes()),
    new Uint256(value),
    new Uint32(epochNumber)
  )
}
