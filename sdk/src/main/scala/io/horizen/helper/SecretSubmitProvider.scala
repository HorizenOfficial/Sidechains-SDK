package io.horizen.helper

import com.horizen.secret.Secret

trait SecretSubmitProvider {

  @throws(classOf[IllegalArgumentException])
  def submitSecret(s: Secret): Unit
}
