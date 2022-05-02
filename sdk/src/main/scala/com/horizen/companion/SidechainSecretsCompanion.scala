package com.horizen.companion

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.horizen.SidechainTypes
import com.horizen.secret.SecretsIdsEnum.{PrivateKey25519SecretId, SchnorrSecretKeyId, VrfPrivateKeySecretId}
import com.horizen.secret.{PrivateKey25519Serializer, SchnorrSecretSerializer, SecretSerializer, VrfSecretKeySerializer}
import com.horizen.utils.DynamicTypedSerializer


case class SidechainSecretsCompanion(customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]])
  extends DynamicTypedSerializer[SidechainTypes#SCS, SecretSerializer[SidechainTypes#SCS]](
    new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]() {{
      put(PrivateKey25519SecretId.id, PrivateKey25519Serializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
      put(VrfPrivateKeySecretId.id, VrfSecretKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
      put(SchnorrSecretKeyId.id, SchnorrSecretSerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
    }},
    customSecretSerializers)
