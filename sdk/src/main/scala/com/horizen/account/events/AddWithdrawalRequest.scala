package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.evm.utils.Address
import com.horizen.proposition.MCPublicKeyHashProposition
import org.web3j.abi.datatypes.generated.{Bytes20, Uint256, Uint32}

import java.math.BigInteger
import scala.annotation.meta.getter

case class AddWithdrawalRequest(
    @(Parameter @getter)(1) @(Indexed @getter) from: Address,
    @(Parameter @getter)(2) @(Indexed @getter) mcDest: Bytes20,
    @(Parameter @getter)(3) value: Uint256,
    @(Parameter @getter)(4) epochNumber: Uint32
) {}

object AddWithdrawalRequest {
  def apply(
      fromAddress: Address,
      mcAddr: MCPublicKeyHashProposition,
      withdrawalAmount: BigInteger,
      epochNum: Int
  ): AddWithdrawalRequest = AddWithdrawalRequest(
    fromAddress,
    new Bytes20(mcAddr.bytes()),
    new Uint256(withdrawalAmount),
    new Uint32(epochNum)
  )
}
