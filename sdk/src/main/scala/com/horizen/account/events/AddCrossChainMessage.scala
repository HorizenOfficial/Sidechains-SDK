package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import org.web3j.abi.datatypes.{Address, DynamicBytes}
import org.web3j.abi.datatypes.generated.{Bytes20, Uint256, Uint32}

import scala.annotation.meta.getter


case class AddCrossChainMessage(@(Parameter@getter)(1) @(Indexed@getter) sender: Address,
                                @(Parameter@getter)(2) @(Indexed@getter) messageType: Uint32,
                                @(Parameter@getter)(3) receiverSidechain: DynamicBytes,
                                @(Parameter@getter)(4) receiver: DynamicBytes,
                                @(Parameter@getter)(5) payload: DynamicBytes
                               ) {
}

object AddCrossChainMessage {
  def apply(sender: Address,
            messageType: Int,
            receiverSidechain: Array[Byte],
            receiver: Array[Byte],
            payload: Array[Byte]): AddCrossChainMessage = {
    new AddCrossChainMessage(
      sender,
      new Uint32(messageType),
      new DynamicBytes(receiverSidechain),
      new DynamicBytes(receiver),
      new DynamicBytes(payload)

    )
  }
}


