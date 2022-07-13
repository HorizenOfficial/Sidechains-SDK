package com.horizen.account.abi

import org.web3j.abi.DefaultFunctionEncoder
import org.web3j.abi.datatypes.{StaticStruct, Type}
import org.web3j.utils.Numeric

import java.util

trait ABIEncodable {
  type M >: this.type <: ABIEncodable

  def encode(): Array[Byte] = {
    val encoder = new DefaultFunctionEncoder()
    val listOfABIObjs: util.List[Type[_]] = util.Arrays.asList(asABIType())
    val encodedString = encoder.encodeParameters(listOfABIObjs)
    Numeric.hexStringToByteArray(encodedString)
  }

  private[horizen] def asABIType(): StaticStruct


}
