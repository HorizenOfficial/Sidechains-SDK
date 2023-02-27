package com.horizen.helper

import com.google.inject.{Inject, Provider}
import com.horizen.AbstractSidechainApp
import com.horizen.secret.Secret
import com.horizen.utxo.SidechainApp

class SecretSubmitHelperImpl @Inject()(val appProvider: Provider[AbstractSidechainApp]) extends SecretSubmitHelper {

  @throws(classOf[IllegalArgumentException])
  override def submitSecret(secret: Secret): Unit = {
    appProvider.get().getSecretSubmitProvider.submitSecret(secret)
  }
}
