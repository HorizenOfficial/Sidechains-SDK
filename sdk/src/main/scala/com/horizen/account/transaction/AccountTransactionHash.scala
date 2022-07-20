package com.horizen.account.transaction

import org.web3j.utils.Numeric
import scorex.crypto.hash.Keccak256

object AccountTransactionHash {
  def getHash(tx: String, prefix: Byte): String = {
    if (prefix == 0) Numeric.toHexString(Keccak256.hash(tx))
    else Numeric.toHexString(Keccak256.prefixedHash(prefix, Numeric.hexStringToByteArray(tx)))
  }
}
