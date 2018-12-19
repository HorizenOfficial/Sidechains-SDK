import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.Try

case class SidechainSecretsCompanion(customSecretSerializers: Map[scorex.core.ModifierTypeId, SecretSerializer[_ <: Secret]])
  extends Serializer[Secret] {

  val coreSecretSerializers: Map[scorex.core.ModifierTypeId, SecretSerializer[_ <: Secret]] =
    Map(new PrivateKey25519(/*args*/).secretTypeId() -> new PrivateKey25519Serializer())

  val customSecretId = ModifierTypeId @@ 0xFF // TODO: think about proper value

  // TO DO: do like in SidechainTransactionsCompanion
  override def toBytes(obj: Secret): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[Secret] = ???
}

