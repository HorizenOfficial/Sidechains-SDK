package com.horizen.companion

import com.horizen.secret.{PrivateKey25519, PrivateKey25519Serializer, Secret, SecretSerializer}
import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.Try

case class SidechainSecretsCompanion(customSecretSerializers: Map[scorex.core.ModifierTypeId, SecretSerializer[_ <: Secret]])
  extends Serializer[Secret] {

  val coreSecretSerializers: Map[scorex.core.ModifierTypeId.Raw , SecretSerializer[_ <: Secret]] =
    Map(new PrivateKey25519(null, null).secretTypeId() -> new PrivateKey25519Serializer[PrivateKey25519]())

  val customSecretId = ModifierTypeId @@ Byte.MaxValue // TO DO: think about proper value

  // TO DO: do like in SidechainTransactionsCompanion
  override def toBytes(obj: Secret): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[Secret] = ???
}

