package com.horizen.account.events

import com.horizen.account.event.annotation.{Indexed, Parameter}
import com.horizen.account.state.KeyRotationProofType
import com.horizen.proposition.SchnorrProposition
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes32, Uint32}

import scala.annotation.meta.getter


case class SubmitKeyRotation(@(Parameter@getter)(1) @(Indexed@getter) keyType: Uint32,
                             @(Parameter@getter)(2) @(Indexed@getter) keyIndex: Uint32,
                             @(Parameter@getter)(3) newKeyValue_1: Bytes1,
                             @(Parameter@getter)(4) newKeyValue_32: Bytes32,
                             @(Parameter@getter)(5) epochNumber: Uint32) {

}

object SubmitKeyRotation {
  def apply(keyType: KeyRotationProofType.Value, keyIndex: Int, newKeyValue: SchnorrProposition, epochNum: Int): SubmitKeyRotation = {
    new SubmitKeyRotation(
      new Uint32(keyType.id),
      new Uint32(keyIndex),
      new Bytes1(newKeyValue.bytes().take(1)),
      new Bytes32(newKeyValue.bytes().drop(1)),
      new Uint32(epochNum)
    )
  }
}

