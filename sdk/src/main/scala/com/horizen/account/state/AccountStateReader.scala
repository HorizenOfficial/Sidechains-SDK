package com.horizen.account.state

import com.horizen.state.StateReader

trait AccountStateReader extends StateReader {
  def getAccount(address: Array[Byte]): Account
  def getBalance(address: Array[Byte]): Long
  // etc.
}
