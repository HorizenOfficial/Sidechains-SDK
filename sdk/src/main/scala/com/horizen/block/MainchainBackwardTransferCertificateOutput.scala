package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.serialization.{JsonBase58Serializer, Views}
import com.horizen.utils.{BytesUtils, Utils}


@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("outputBytes"))
case class MainchainBackwardTransferCertificateOutput
  (outputBytes: Array[Byte],
   @JsonSerialize(using = classOf[JsonBase58Serializer]) pubKeyHash: Array[Byte],
   amount: Long)
{

  def size: Int = outputBytes.length

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(outputBytes))

}

object MainchainBackwardTransferCertificateOutput {
  def parse(outputBytes: Array[Byte], offset: Int): MainchainBackwardTransferCertificateOutput = {

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(outputBytes, currentOffset)
    currentOffset += 8

    val pubKeyHash: Array[Byte] = BytesUtils.reverseBytes(outputBytes.slice(currentOffset, currentOffset + 20))
    currentOffset += 20

    new MainchainBackwardTransferCertificateOutput(outputBytes.slice(offset, currentOffset), pubKeyHash, amount)
  }
}
