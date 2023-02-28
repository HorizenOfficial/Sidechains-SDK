package io.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.horizen.json.Views
import io.horizen.json.serializer.JsonHorizenPublicKeyHashSerializer
import io.horizen.utils.{BytesUtils, Utils}


@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("outputBytes", "hash"))
case class MainchainBackwardTransferCertificateOutput
  (outputBytes: Array[Byte],
   @JsonProperty("address") @JsonSerialize(using = classOf[JsonHorizenPublicKeyHashSerializer]) pubKeyHash: Array[Byte],
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

    // Get and store Horizen public key hash bytes in original LE endianness
    val pubKeyHash: Array[Byte] = outputBytes.slice(currentOffset, currentOffset + 20)
    currentOffset += 20

    new MainchainBackwardTransferCertificateOutput(outputBytes.slice(offset, currentOffset), pubKeyHash, amount)
  }
}
