package com.horizen.account.abi

import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

import java.util

object ABIUtil {

  val METHOD_CODE_LENGTH = 4

  def getArgumentsFromData(data: Array[Byte]): Array[Byte] = {
    require(data.length >= METHOD_CODE_LENGTH, s"Data length ${data.length} must be >= ${METHOD_CODE_LENGTH}")
    util.Arrays.copyOfRange(data, METHOD_CODE_LENGTH, data.length)
  }

  def getOpCodeFromData(data: Array[Byte]): Array[Byte] = {
    require(data.length >= METHOD_CODE_LENGTH, s"Data length ${data.length} must be >= ${METHOD_CODE_LENGTH}")
    util.Arrays.copyOf(data, METHOD_CODE_LENGTH)
  }


  def getABIMethodId(methodSig: String): String = Numeric.toHexStringNoPrefix(Hash.sha3(methodSig.getBytes)).substring(0, 8)

}
