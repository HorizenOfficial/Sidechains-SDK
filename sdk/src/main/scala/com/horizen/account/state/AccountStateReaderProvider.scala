package com.horizen.account.state

trait AccountStateReaderProvider {
  def getAccountStateReader(): AccountStateReader
}
