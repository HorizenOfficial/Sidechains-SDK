package com.horizen.account.state

import com.horizen.state.StateReader

trait AccountStateReader extends StateReader {

  def getAccount(address: Array[Byte]): Account
  def getBalance(address: Array[Byte]): java.math.BigInteger
  // etc.

  def getAccountStateRoot: Option[Array[Byte]] // 32 bytes, kessack hash

}
