package com.horizen.account.abi

import com.horizen.account.state.ExecutionFailedException
import com.horizen.account.utils.Secp256k1
import com.horizen.utils.BytesUtils
import org.web3j.utils.Numeric
import scorex.crypto.hash.Keccak256

import java.nio.charset.StandardCharsets
import java.util

object ABIUtil {

  val METHOD_CODE_LENGTH = 4

  @throws(classOf[ExecutionFailedException])
  def getArgumentsFromData(data: Array[Byte]): Array[Byte] = getSlice(data, METHOD_CODE_LENGTH, data.length)

  @throws(classOf[ExecutionFailedException])
  def getFunctionSignature(data: Array[Byte]): String = BytesUtils.toHexString(getSlice(data, 0, METHOD_CODE_LENGTH))

  def getABIMethodId(methodSig: String): String = Numeric.toHexStringNoPrefix(Keccak256.hash(methodSig.getBytes(StandardCharsets.UTF_8)).take(4))

  @throws(classOf[ExecutionFailedException])
  private def getSlice(data: Array[Byte], from: Int, to: Int) = {
    if (data.length < METHOD_CODE_LENGTH) {
      throw new ExecutionFailedException(s"Data length ${data.length} must be >= $METHOD_CODE_LENGTH")
    }
    util.Arrays.copyOfRange(data, from, to)
  }
}
