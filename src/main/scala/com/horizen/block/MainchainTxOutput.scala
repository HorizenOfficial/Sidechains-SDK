package com.horizen.block

import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.util.Try

case class MainchainTxOutput(
                       value: Long,
                       script: Array[Byte]
                     )


trait CrosschainTxOutput extends BytesSerializable {
  val outputType: Byte
  val hash: Array[Byte]
}


class MainchainTxForwardTransferOutput extends CrosschainTxOutput {
  override val outputType: Byte = 1

  override lazy val hash: Array[Byte] = ???

  override type M = MainchainTxForwardTransferOutput

  override lazy val serializer = MainchainTxForwardTransferOutputSerializer
}

object MainchainTxForwardTransferOutputSerializer extends Serializer[MainchainTxForwardTransferOutput] {
  override def toBytes(obj: MainchainTxForwardTransferOutput): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[MainchainTxForwardTransferOutput] = ???
}


class MainchainTxCertifierLockOutput extends CrosschainTxOutput {
  override val outputType: Byte = 2

  override lazy val hash: Array[Byte] = ???

  override type M = MainchainTxCertifierLockOutput

  override def serializer = MainchainTxCertifierLockOutputSerializer
}

object MainchainTxCertifierLockOutputSerializer extends Serializer[MainchainTxCertifierLockOutput] {
  override def toBytes(obj: MainchainTxCertifierLockOutput): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[MainchainTxCertifierLockOutput] = ???
}
