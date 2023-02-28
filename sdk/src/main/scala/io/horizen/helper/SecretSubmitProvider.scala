package io.horizen.helper

import io.horizen.secret.Secret

trait SecretSubmitProvider {

  @throws(classOf[IllegalArgumentException])
  def submitSecret(s: Secret): Unit
}
