package io.horizen.companion

import io.horizen.SidechainTypes
import io.horizen.account.secret.PrivateKeySecp256k1Serializer
import io.horizen.secret.SecretsIdsEnum.{PrivateKey25519SecretId, PrivateKeySecp256k1SecretId, SchnorrSecretKeyId, VrfPrivateKeySecretId}
import io.horizen.secret.{PrivateKey25519Serializer, SchnorrSecretSerializer, SecretSerializer, VrfSecretKeySerializer}
import io.horizen.utils.{CheckedCompanion, DynamicTypedSerializer}

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

case class SidechainSecretsCompanion(customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]])
  extends DynamicTypedSerializer[SidechainTypes#SCS, SecretSerializer[SidechainTypes#SCS]](
    new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]() {{
      put(PrivateKey25519SecretId.id, PrivateKey25519Serializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
      put(VrfPrivateKeySecretId.id, VrfSecretKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
      put(SchnorrSecretKeyId.id, SchnorrSecretSerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
      put(PrivateKeySecp256k1SecretId.id, PrivateKeySecp256k1Serializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
    }},
    customSecretSerializers
  ) with CheckedCompanion[SidechainTypes#SCS, SecretSerializer[SidechainTypes#SCS]]
