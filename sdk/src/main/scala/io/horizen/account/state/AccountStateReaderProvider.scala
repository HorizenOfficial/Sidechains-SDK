package io.horizen.account.state

trait AccountStateReaderProvider {
  def getAccountStateReader(): AccountStateReader
}
