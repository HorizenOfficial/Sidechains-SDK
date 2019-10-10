package com.horizen.api.http

class SidechainApiMockConfiguration {

  private var walletAddSecretReturnValue : Boolean = true
  private var historyValidBlockIdReturnValue : Boolean = true

  def getWalletAddSecretReturnValue() : Boolean = walletAddSecretReturnValue

  def setWalletAddSecretReturnValue(value : Boolean) : Unit = walletAddSecretReturnValue = value

  def getHistoryValidBlockIdReturnValue() : Boolean = historyValidBlockIdReturnValue

  def setHistoryValidBlockIdReturnValue(value : Boolean) : Unit = historyValidBlockIdReturnValue = value

}
