package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.account.proposition.AddressProposition
import com.horizen.utils.BytesUtils
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Bytes32

import scala.annotation.meta.getter


case class WithdrawForgerStake(@(Parameter@getter)(1) @(Indexed@getter) owner: Address,
                               @(Parameter@getter)(2) stakeId: Bytes32)

object WithdrawForgerStake {
  def apply(owner: AddressProposition, stakeId: Array[Byte]): WithdrawForgerStake = {
    new WithdrawForgerStake(new Address(BytesUtils.toHexString(owner.address())),
      new Bytes32(stakeId))
  }
}