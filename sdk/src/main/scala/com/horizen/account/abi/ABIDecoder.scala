package com.horizen.account.abi

import org.web3j.abi.datatypes.Type
import org.web3j.abi.{DefaultFunctionReturnDecoder, TypeReference}

import java.util
import scala.util.Try

object ABIDecoder {
  val OP_CODE_LENGTH = 4

  def getArgumentsFromData(data: Array[Byte]): Array[Byte] = {
    if (data.length < ABIDecoder.OP_CODE_LENGTH) throw new IllegalArgumentException("Data length " + data.length + " must be >= " + ABIDecoder.OP_CODE_LENGTH)
    util.Arrays.copyOfRange(data, ABIDecoder.OP_CODE_LENGTH, data.length)
  }

  def getOpCodeFromData(data: Array[Byte]): Array[Byte] = {
    if (data.length < ABIDecoder.OP_CODE_LENGTH) throw new IllegalArgumentException("Data length " + data.length + " must be >= " + ABIDecoder.OP_CODE_LENGTH)
    util.Arrays.copyOf(data, ABIDecoder.OP_CODE_LENGTH)
  }

}

trait ABIDecoder[T] {
  private val decoder = new DefaultFunctionReturnDecoder()
  val ListOfABIParamTypes: util.List[TypeReference[Type[_]]]
  lazy val ABIDataParamsLengthInBytes = Type.MAX_BYTE_LENGTH * ListOfABIParamTypes.size()

  def decode(abiEncodedData: Array[Byte]): Try[T] = {
    Try {
      val inputParams = ABIDecoder.getArgumentsFromData(abiEncodedData)
      require(inputParams.length == ABIDataParamsLengthInBytes, s"Wrong message data field length: ${inputParams.length}")
      val inputParamsString = org.web3j.utils.Numeric.toHexString(inputParams)
      val listOfParams = decoder.decodeFunctionResult(inputParamsString, ListOfABIParamTypes)
      createType(listOfParams)
    }
  }

  def createType(listOfParams: util.List[Type[_]]): T


}
