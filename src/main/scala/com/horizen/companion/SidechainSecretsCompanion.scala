package com.horizen.companion

import com.horizen.secret.{PrivateKey25519, PrivateKey25519Serializer, Secret, SecretSerializer}
import com.horizen.utils.SerializableCompanion

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

case class SidechainSecretsCompanion(customSerializers: JHashMap[JByte, SecretSerializer[_ <: Secret]])
  extends SerializableCompanion[Secret, SecretSerializer[_ <: Secret]](
    new JHashMap[JByte, SecretSerializer[_ <: Secret]]() {{
      put(PrivateKey25519.SECRET_TYPE_ID, PrivateKey25519Serializer.getSerializer)
    }},
    customSerializers)
