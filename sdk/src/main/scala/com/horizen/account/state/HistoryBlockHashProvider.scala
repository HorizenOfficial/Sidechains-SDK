package com.horizen.account.state

trait HistoryBlockHashProvider {
  def blockIdByHeight(height: Int): Option[String]
}
