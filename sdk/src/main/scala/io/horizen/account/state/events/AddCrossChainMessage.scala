package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.generated.{Bytes32, Uint32}

import scala.annotation.meta.getter

case class AddCrossChainMessage(@(Parameter@getter)(1) @(Indexed@getter) sender: Address,
                                @(Parameter@getter)(2) @(Indexed@getter) messageType: Uint32,
                                @(Parameter@getter)(3) receiverSidechain: DynamicBytes,
                                @(Parameter@getter)(4) receiver: DynamicBytes,
                                @(Parameter@getter)(5) payload: Bytes32
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
      new Bytes32(payload)
    )
  }
}


