package io.horizen.account.state.events

import io.horizen.account.state.events.annotation.{Indexed, Parameter}
import io.horizen.evm.Address
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes20, Bytes32, Bytes4, Uint32}
import org.web3j.abi.datatypes.{Address => AbiAddress}

import scala.annotation.meta.getter

case class AddCrossChainMessage(@(Parameter@getter)(1) @(Indexed@getter) sender: AbiAddress,
                                @(Parameter@getter)(2) @(Indexed@getter) messageType: Uint32,
                                @(Parameter@getter)(3) receiverSidechain: Bytes32,
                                @(Parameter@getter)(4) receiver: Bytes20,
                                @(Parameter@getter)(5) payload: Bytes4
                               ) {
}

object AddCrossChainMessage {
  def apply(sender: Address,
            messageType: Int,
            receiverSidechain: Array[Byte],
            receiver: Array[Byte],
            payload: Array[Byte]): AddCrossChainMessage = {
    new AddCrossChainMessage(
      new AbiAddress(sender.toString),
      new Uint32(messageType),
      new Bytes32(receiverSidechain),
      new Bytes20(receiver),
      new Bytes4(payload)
    )
  }
}


