package com.horizen.block

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.utils.{BytesUtils, Utils, VarInt}

import scala.util.Try

case class FieldElementCertificateFieldConfig(nBits: Int)
case class BitVectorCertificateFieldConfig(bitVectorSizeBits: Int, maxCompressedSizeBytes: Int)

case class MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes: Array[Byte],
                                                            withdrawalEpochLength: Int,
                                                            amount: Long,
                                                            address: Array[Byte],
                                                            customData: Array[Byte],
                                                            constant: Option[Array[Byte]],
                                                            certVk: Array[Byte],
                                                            mbtrVk: Option[Array[Byte]],
                                                            ceasedVk: Option[Array[Byte]],
                                                            fieldElementCertificateFieldConfigs: Seq[FieldElementCertificateFieldConfig],
                                                            bitVectorCertificateFieldConfigs: Seq[BitVectorCertificateFieldConfig]) {
  def size: Int = sidechainCreationOutputBytes.length
}

object MainchainTxSidechainCreationCrosschainOutputData {
  val OUTPUT_TYPE: Byte = 3.toByte

  def create(sidechainCreationOutputBytes: Array[Byte], offset: Int): Try[MainchainTxSidechainCreationCrosschainOutputData] = Try {

    var currentOffset: Int = offset

    val withdrawalEpochLength: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 4

    val amount: Long = BytesUtils.getReversedLong(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 8

    val address: Array[Byte] = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val customDataLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += customDataLength.size()

    val customData: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + customDataLength.value().intValue())
    currentOffset += customDataLength.value().intValue()

    val constantLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += constantLength.size()

    val constantPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val constant: Option[Array[Byte]] = if(constantPresence) {
      Some(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + constantLength.value().intValue()))
    } else {
      None
    }
    if(constantPresence) currentOffset += constantLength.value().intValue()

    val vkSize: Int = CryptoLibProvider.sigProofThresholdCircuitFunctions.certVkSize()
    val certVk: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + vkSize)
    currentOffset += vkSize

    val mbtrVkPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val mbtrVk: Option[Array[Byte]] = if(mbtrVkPresence) {
      Some(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + vkSize))
    } else {
      None
    }
    if(mbtrVkPresence) currentOffset += vkSize

    val ceasedVkPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val ceasedVk: Option[Array[Byte]] = if(ceasedVkPresence) {
      Some(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + vkSize))
    } else {
      None
    }
    if(ceasedVkPresence) currentOffset += vkSize

    val fieldElementCertificateFieldConfigsLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += fieldElementCertificateFieldConfigsLength.size()

    val fieldElementCertificateFieldConfigs: Seq[FieldElementCertificateFieldConfig] =
      (1 to fieldElementCertificateFieldConfigsLength.size()).map ( _ => {
        val nBits: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
        currentOffset += 4
        FieldElementCertificateFieldConfig(nBits)
      })

    val bitVectorCertificateFieldConfigsLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += bitVectorCertificateFieldConfigsLength.size()

    val bitVectorCertificateFieldConfigs: Seq[BitVectorCertificateFieldConfig] =
      (1 to bitVectorCertificateFieldConfigsLength.size()).map ( _ => {
        val bitVectorSizeBits: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
        currentOffset += 4
        val maxCompressedSizeBytes: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
        currentOffset += 4
        BitVectorCertificateFieldConfig(bitVectorSizeBits, maxCompressedSizeBytes)
      })

    MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes.slice(offset, currentOffset),
      withdrawalEpochLength, amount, address, customData, constant, certVk, mbtrVk, ceasedVk,
      fieldElementCertificateFieldConfigs, bitVectorCertificateFieldConfigs)
  }
}

class MainchainTxSidechainCreationCrosschainOutput(override val sidechainId: Array[Byte], data: MainchainTxSidechainCreationCrosschainOutputData)
                                                    extends MainchainTxSidechainCreationCrosschainOutputData(
                                                      data.sidechainCreationOutputBytes,
                                                      data.withdrawalEpochLength,
                                                      data.amount,
                                                      data.address,
                                                      data.customData,
                                                      data.constant,
                                                      data.certVk,
                                                      data.mbtrVk,
                                                      data.ceasedVk,
                                                      data.fieldElementCertificateFieldConfigs,
                                                      data.bitVectorCertificateFieldConfigs
                                                    ) with MainchainTxCrosschainOutput {

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(sidechainCreationOutputBytes))
}


object MainchainTxSidechainCreationCrosschainOutput {
  def calculateSidechainId(transactionHash: Array[Byte], index: Int): Array[Byte] = {
    val scId: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(transactionHash), BytesUtils.reverseBytes(Ints.toByteArray(index))))
    // TODO temporary until we can use a PoseidonHash instead of a SHA one
    // TODO: fix as soon as will be changed on MC side.
    //----
    // clear last two bits for rendering it a valid tweedle field element
    scId(scId.length - 1) = (scId.last & 0x3f).byteValue()
    scId
  }
}

