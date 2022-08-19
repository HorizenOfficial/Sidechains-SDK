package com.horizen.account.abi

import com.horizen.account.state.ExecutionFailedException
import com.horizen.utils.BytesUtils
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

import java.util

object ABIUtil {

  val METHOD_CODE_LENGTH = 4

  def getArgumentsFromData(data: Array[Byte]): Array[Byte] = getSlice(data, METHOD_CODE_LENGTH, data.length)

  def getFunctionSignature(data: Array[Byte]): String = BytesUtils.toHexString(getSlice(data, 0, METHOD_CODE_LENGTH))

  def getABIMethodId(methodSig: String): String = Numeric.toHexStringNoPrefix(Hash.sha3(methodSig.getBytes).take(4))

  private def getSlice(data: Array[Byte], from: Int, to: Int) = {
    if (data.length < METHOD_CODE_LENGTH) {
      throw new ExecutionFailedException(s"Data length ${data.length} must be >= $METHOD_CODE_LENGTH")
    }
    util.Arrays.copyOfRange(data, from, to)
  }
}
