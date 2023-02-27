package com.horizen.account.mempool

object TxExecutableStatus extends Enumeration {
  type TxExecutableStatus = Value
  val EXEC, NON_EXEC = Value
}