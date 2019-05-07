package com.horizen.block

import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.Try

trait MainchainTxOutput extends BytesSerializable
// inchainTxOutput
// crosschainTxOutput

class MainchainTxOutputV1(
                           val value: Long,
                           val script: Array[Byte]
                         ) extends MainchainTxOutput {
  override type M = MainchainTxOutputV1

  override def serializer: Serializer[MainchainTxOutputV1] = ???
}


// TO DO: Define V2 outputs structure then implement different MainchainTxOutputV2
trait MainchainTxOutputV2 extends MainchainTxOutput {
  val outputType: Byte

  val hash: Array[Byte]
}


class MainchainTxForwardTransferOutput extends MainchainTxOutputV2 {
  override val outputType: Byte = ???

  override lazy val hash: Array[Byte] = ???

  override type M = MainchainTxForwardTransferOutput

  override lazy val serializer = MainchainTxForwardTransferOutputSeializer
}

object MainchainTxForwardTransferOutputSeializer extends Serializer[MainchainTxForwardTransferOutput] {
  override def toBytes(obj: MainchainTxForwardTransferOutput): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[MainchainTxForwardTransferOutput] = ???
}


class MainchainTxCertifierLockOutput extends MainchainTxOutputV2 {
  override val outputType: Byte = ???

  override lazy val hash: Array[Byte] = ???

  override type M = MainchainTxCertifierLockOutput

  override def serializer = MainchainTxCertifierLockOutputSeializer
}

object MainchainTxCertifierLockOutputSeializer extends Serializer[MainchainTxCertifierLockOutput] {
  override def toBytes(obj: MainchainTxCertifierLockOutput): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[MainchainTxCertifierLockOutput] = ???
}
