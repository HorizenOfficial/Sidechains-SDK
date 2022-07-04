package com.horizen.account.state

import com.horizen.state.StateReader

import scala.util.Try

trait AccountStateReader extends StateReader {

  def getAccount(address: Array[Byte]): Account
  def getCodeHash(address: Array[Byte]): Array[Byte]
  def getBalance(address: Array[Byte]): Try[java.math.BigInteger]
  def getNonce(address: Array[Byte]): Try[java.math.BigInteger]
  // etc.

  def getAccountStateRoot: Option[Array[Byte]] // 32 bytes, kessack hash

}
