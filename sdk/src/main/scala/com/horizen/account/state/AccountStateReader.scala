package com.horizen.account.state

import com.horizen.state.StateReader

trait AccountStateReader extends StateReader {

  def getAccount(address: Array[Byte]): Account
  def getBalance(address: Array[Byte]): Long
  def getCodeHash(address: Array[Byte]): Array[Byte]
  // etc.

  def getAccountStateRoot: Option[Array[Byte]] // 32 bytes, kessack hash

}
