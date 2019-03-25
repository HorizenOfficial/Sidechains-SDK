package com.horizen.companion

import com.horizen.secret.{PrivateKey25519, PrivateKey25519Serializer, Secret, SecretSerializer}
import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.{Failure, Try}
import scala.collection.mutable.{HashMap, Map}
import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes


case class SidechainSecretsCompanion(customSecretSerializers: Map[scorex.core.ModifierTypeId.Raw, SecretSerializer[_ <: Secret]])
  extends Serializer[Secret]
{

  val coreSecretSerializers: Map[scorex.core.ModifierTypeId.Raw , SecretSerializer[_ <: Secret]] =
    Map(PrivateKey25519.SECRET_TYPE_ID ->  PrivateKey25519Serializer.getSerializer)

  val coreSecretType : ModifierTypeId = ModifierTypeId @@ (0: Byte)
  val customSecretType : ModifierTypeId = ModifierTypeId @@ Byte.MaxValue // TO DO: think about proper value

  def registerCustomSerializer (secret : Secret) : Unit = {
    customSecretSerializers.put(secret.secretTypeId(), secret.serializer())
  }

  def unregisterCustomSerializer (secret: Secret) : Unit = {
    customSecretSerializers.remove(secret.secretTypeId())
  }

  override def toBytes(secret: Secret): Array[Byte] = {
    secret match {
      case s: PrivateKey25519 => Bytes.concat(Array(coreSecretType), Array(secret.secretTypeId()),
        secret.serializer().asInstanceOf[SecretSerializer[Secret]].toBytes(secret))
      case _ => Bytes.concat(Array(customSecretType), Array(secret.secretTypeId()),
        secret.serializer().asInstanceOf[SecretSerializer[Secret]].toBytes(secret))
    }
  }

  override def parseBytes(bytes: Array[Byte]): Try[Secret] = {
    val secretType = ModifierTypeId @@ bytes(0)
    val secretTypeId = ModifierTypeId @@ bytes(1)
    secretType match {
      case `coreSecretType` => coreSecretSerializers.get(secretTypeId) match {
        case Some(s) => s.parseBytes(bytes.drop(2))
        case None => Failure(new MatchError("Unknown core secret type id"))
      }
      case `customSecretType` => customSecretSerializers.get(secretTypeId) match {
        case Some(s) => s.parseBytes(bytes.drop(2))
        case None => Failure(new MatchError("Unknown custom secret type id"))
      }
      case _ => Failure(new MatchError("Unknown secret type"))
    }
  }
}

object SidechainSecretsCompanion {

  val sidechainSecretsCompanion : SidechainSecretsCompanion = new SidechainSecretsCompanion(new HashMap[scorex.core.ModifierTypeId.Raw, SecretSerializer[_ <: Secret]]())

}


