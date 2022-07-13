package com.horizen.account.abi

import org.web3j.abi.DefaultFunctionEncoder
import org.web3j.abi.datatypes.{DynamicArray, StaticStruct}
import org.web3j.utils.Numeric

import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter

trait ABIListEncoder[M <: ABIEncodable] {
  val encoder = new DefaultFunctionEncoder()

  def encode(listOfObj: Seq[M]): Array[Byte] = {

    val listOfABIObj = listOfObj.map(wr => wr.asABIType()).asJava
    Numeric.hexStringToByteArray(encoder.encodeParameters(util.Arrays.asList(new DynamicArray[StaticStruct](classOf[StaticStruct], listOfABIObj))))

  }

}
