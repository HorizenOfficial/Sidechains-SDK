package com.horizen.companion

import com.horizen.secret.{PrivateKey25519, PrivateKey25519Serializer, Secret, SecretSerializer}
import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.{Failure, Try}
import scala.collection.mutable.{HashMap, Map}
import com.google.common.primitives.Bytes

case class SidechainSecretsCompanion(customSecretSerializers: Map[Byte, SecretSerializer[_ <: Secret]])
  extends Serializer[Secret]
{

  val coreSecretSerializers: Map[Byte, SecretSerializer[_ <: Secret]] =
    Map(PrivateKey25519.SECRET_TYPE_ID ->  PrivateKey25519Serializer.getSerializer)

  val customSecretType : Byte = Byte.MaxValue // TO DO: think about proper value

  override def toBytes(secret: Secret): Array[Byte] = {
    secret match {
      case s: PrivateKey25519 => Bytes.concat(Array(s.secretTypeId()),
        PrivateKey25519Serializer.getSerializer.toBytes(s))
      case _ => customSecretSerializers.get(secret.secretTypeId()) match {
        case Some(serializer) => Bytes.concat(Array(customSecretType), Array(secret.secretTypeId()),
          serializer.asInstanceOf[SecretSerializer[Secret]].toBytes(secret))
        case None => throw new IllegalArgumentException("Unknown secret type - " + secret)
      }

    }
  }

  override def parseBytes(bytes: Array[Byte]): Try[Secret] = {
    val secretType = bytes(0)
    secretType match {
      case `customSecretType` => customSecretSerializers.get(bytes(1)) match {
        case Some(s) => s.parseBytes(bytes.drop(2))
        case None => Failure(new MatchError("Unknown custom secret type id"))
      }
      case _ => coreSecretSerializers.get(secretType) match {
        case Some(s) => s.parseBytes(bytes.drop(1))
        case None => Failure(new MatchError("Unknown core secret type id"))
      }
    }
  }
}
