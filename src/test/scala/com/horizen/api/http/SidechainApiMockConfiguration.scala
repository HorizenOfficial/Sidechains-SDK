package com.horizen.api.http

class SidechainApiMockConfiguration {

  private var walletAddSecretReturnValue : Boolean = true

  def getWalletAddSecretReturnValue() : Boolean = walletAddSecretReturnValue

  def setWalletAddSecretReturnValue(value : Boolean) : Unit = walletAddSecretReturnValue = value

}
