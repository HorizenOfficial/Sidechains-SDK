package com.horizen.companion

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.horizen.SidechainTypes
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Serializer, SecretSerializer, VrfSecretKey, VrfSecretKeySerializer}
import com.horizen.utils.DynamicTypedSerializer

case class SidechainSecretsCompanion(customSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]])
  extends DynamicTypedSerializer[SidechainTypes#SCS, SecretSerializer[SidechainTypes#SCS]](
    new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]() {{
      put(PrivateKey25519.SECRET_TYPE_ID, PrivateKey25519Serializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
      put(VrfSecretKey.SECRET_TYPE_ID, VrfSecretKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
    }},
    customSerializers)
