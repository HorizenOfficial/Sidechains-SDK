package com.horizen.block

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.cryptolibprovider.{CryptoLibProvider, FieldElementUtils}
import com.horizen.serialization.ReverseBytesSerializer
import com.horizen.utils.{BytesUtils, Utils, VarInt}

import scala.util.Try

case class MainchainTxBwtRequestCrosschainOutput(bwtRequestOutputBytes: Array[Byte],
                                                 @JsonSerialize(using = classOf[ReverseBytesSerializer]) override val sidechainId: Array[Byte], // uint256
                                                 scRequestData: Array[Array[Byte]],     // vector<ScFieldElement>
                                                 @JsonSerialize(using = classOf[ReverseBytesSerializer]) mcDestinationAddress: Array[Byte],     // uint160
                                                 scFee: Long                            // CAmount (int64_t)
                                                ) extends MainchainTxCrosschainOutput {

  override lazy val hash: Array[Byte] = Utils.doubleSHA256Hash(bwtRequestOutputBytes)

  def size: Int = bwtRequestOutputBytes.length
}

object MainchainTxBwtRequestCrosschainOutput {
  def create(bwtRequestOutputBytes: Array[Byte], offset: Int): Try[MainchainTxBwtRequestCrosschainOutput] = Try {
    if(offset < 0)
      throw new IllegalArgumentException("Input data corrupted. Offset is negative.")

    var currentOffset: Int = offset

    val sidechainId: Array[Byte] = bwtRequestOutputBytes.slice(currentOffset, currentOffset + 32)
    currentOffset += 32

    val scRequestDataSize: VarInt = BytesUtils.getReversedVarInt(bwtRequestOutputBytes, currentOffset)
    currentOffset += scRequestDataSize.size()

    val scRequestDataSeq: Seq[Array[Byte]] = (1 to scRequestDataSize.value().intValue()).map(idx => {
      val dataSize = BytesUtils.getReversedVarInt(bwtRequestOutputBytes, currentOffset)
      currentOffset += dataSize.size()

      if(dataSize.value() != FieldElementUtils.fieldElementLength())
        throw new IllegalArgumentException(s"Input data corrupted: scRequestData[$idx] size ${dataSize.value()} " +
          s"is expected to be FieldElement size ${FieldElementUtils.fieldElementLength()}")

      val scRequestData: Array[Byte] = bwtRequestOutputBytes.slice(currentOffset, currentOffset + dataSize.value().intValue())
      currentOffset += dataSize.value().intValue()

      scRequestData
    })

    val mcDestinationAddress: Array[Byte] = bwtRequestOutputBytes.slice(currentOffset, currentOffset + 20)
    currentOffset += 20

    val scFee: Long = BytesUtils.getReversedLong(bwtRequestOutputBytes, currentOffset)
    currentOffset += 8

    new MainchainTxBwtRequestCrosschainOutput(bwtRequestOutputBytes.slice(offset, currentOffset),
      sidechainId, scRequestDataSeq.toArray, mcDestinationAddress, scFee)
  }
}
