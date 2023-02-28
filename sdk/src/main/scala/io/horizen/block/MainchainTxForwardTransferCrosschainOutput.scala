package io.horizen.block

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.json.serializer.ReverseBytesSerializer
import com.horizen.utils.{BytesUtils, Utils}

import scala.util.Try

class MainchainTxForwardTransferCrosschainOutput(
                                        val forwardTransferOutputBytes: Array[Byte],
                                        @JsonSerialize(using = classOf[ReverseBytesSerializer]) override val sidechainId: Array[Byte],
                                        val amount: Long,
                                        @JsonSerialize(using = classOf[ReverseBytesSerializer]) val propositionBytes: Array[Byte],
                                        @JsonSerialize(using = classOf[ReverseBytesSerializer]) val mcReturnAddress: Array[Byte]
                                      ) extends MainchainTxCrosschainOutput {

  override lazy val hash: Array[Byte] = Utils.doubleSHA256Hash(forwardTransferOutputBytes)

  override def toString: String = s"FT Output in BigEndian: {\n" +
    s"scid = ${BytesUtils.toHexString(BytesUtils.reverseBytes(sidechainId))}\n" +
    s"amount = $amount\n" +
    s"address = ${BytesUtils.toHexString(BytesUtils.reverseBytes(propositionBytes))}\n}"
}


object MainchainTxForwardTransferCrosschainOutput {
  val FORWARD_TRANSFER_OUTPUT_SIZE = 92 // 8 + 32 + 32 + 20

  def create(forwardTransferOutputBytes: Array[Byte], offset: Int): Try[MainchainTxForwardTransferCrosschainOutput] = Try {
    if(offset < 0 || forwardTransferOutputBytes.length - offset < FORWARD_TRANSFER_OUTPUT_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(forwardTransferOutputBytes, currentOffset)
    currentOffset += 8

    val propositionBytes: Array[Byte] = forwardTransferOutputBytes.slice(currentOffset, currentOffset + 32)
    currentOffset += 32

    val sidechainId: Array[Byte] = forwardTransferOutputBytes.slice(currentOffset, currentOffset + 32)
    currentOffset += 32

    val mcReturnAddress: Array[Byte] = forwardTransferOutputBytes.slice(currentOffset, currentOffset + 20)
    currentOffset += 20

    new MainchainTxForwardTransferCrosschainOutput(forwardTransferOutputBytes.slice(offset, currentOffset), sidechainId, amount, propositionBytes, mcReturnAddress)
  }
}

