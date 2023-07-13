package io.horizen.block

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.commitmenttreenative.{CustomBitvectorElementsConfig, CustomFieldElementsConfig}
import com.horizen.librustsidechains.{Utils => ScCryptoUtils}
import io.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import io.horizen.json.serializer.{ReverseBytesOptSerializer, ReverseBytesSerializer}
import io.horizen.utils.{BytesUtils, CompactSize, Utils}

import scala.util.Try

case class MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes: Array[Byte],
                                                            version: SidechainCreationVersion,
                                                            withdrawalEpochLength: Int,
                                                            amount: Long,
                                                            @JsonSerialize(using = classOf[ReverseBytesSerializer]) address: Array[Byte],
                                                            @JsonSerialize(using = classOf[ReverseBytesSerializer]) customCreationData: Array[Byte],
                                                            @JsonSerialize(using = classOf[ReverseBytesOptSerializer]) constantOpt: Option[Array[Byte]], // TODO
                                                            @JsonSerialize(using = classOf[ReverseBytesSerializer]) certVk: Array[Byte],
                                                            @JsonSerialize(using = classOf[ReverseBytesOptSerializer]) ceasedVkOpt: Option[Array[Byte]], // TODO
                                                            fieldElementCertificateFieldConfigs: Seq[CustomFieldElementsConfig],
                                                            bitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig],
                                                            ftMinAmount: Long,
                                                            btrFee: Long,
                                                            mainchainBackwardTransferRequestDataLength: Byte) {
  def size: Int = sidechainCreationOutputBytes.length
}

object MainchainTxSidechainCreationCrosschainOutputData {

  def create(sidechainCreationOutputBytes: Array[Byte], offset: Int): Try[MainchainTxSidechainCreationCrosschainOutputData] = Try {
    if(offset < 0)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val withdrawalEpochLengthAndVersion: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 4

    val version: SidechainCreationVersion = SidechainCreationVersions.getVersion(withdrawalEpochLengthAndVersion >> 24)

    val withdrawalEpochLength: Int = withdrawalEpochLengthAndVersion & 0x00FFFFFF

    val amount: Long = BytesUtils.getReversedLong(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 8

    val address: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + 32)
    currentOffset += 32

    val customCreationDataLength: CompactSize = BytesUtils.getCompactSize(sidechainCreationOutputBytes, currentOffset)
    currentOffset += customCreationDataLength.size()

    val customCreationData: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + customCreationDataLength.value().intValue())
    currentOffset += customCreationDataLength.value().intValue()

    val constantPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val constantOpt: Option[Array[Byte]] = if(constantPresence) {
      val constantLength: CompactSize = BytesUtils.getCompactSize(sidechainCreationOutputBytes, currentOffset)
      currentOffset += constantLength.size()
      val constant = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + constantLength.value().intValue())
      currentOffset += constantLength.value().intValue()
      Some(constant)
    } else {
      None
    }

    val certVkSize: CompactSize = BytesUtils.getCompactSize(sidechainCreationOutputBytes, currentOffset)
    currentOffset += certVkSize.size()
    val certVk: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + certVkSize.value().intValue())
    currentOffset += certVkSize.value().intValue()

    val ceasedVkPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val ceasedVk: Option[Array[Byte]] = if(ceasedVkPresence) {
      val ceasedVkSize: CompactSize = BytesUtils.getCompactSize(sidechainCreationOutputBytes, currentOffset)
      currentOffset += ceasedVkSize.size()
      val ceasedVk = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + ceasedVkSize.value().intValue())
      currentOffset += ceasedVkSize.value().intValue()
      Some(ceasedVk)
    } else {
      None
    }

    val fieldElementCertificateFieldConfigsLength: CompactSize = BytesUtils.getCompactSize(sidechainCreationOutputBytes, currentOffset)
    currentOffset += fieldElementCertificateFieldConfigsLength.size()

    val fieldElementCertificateFieldConfigs: Seq[CustomFieldElementsConfig] =
      (1 to fieldElementCertificateFieldConfigsLength.value().intValue()).map ( _ => {
        val nBits: Byte = sidechainCreationOutputBytes(currentOffset)
        currentOffset += 1
        new CustomFieldElementsConfig(nBits)
      })

    val bitVectorCertificateFieldConfigsLength: CompactSize = BytesUtils.getCompactSize(sidechainCreationOutputBytes, currentOffset)
    currentOffset += bitVectorCertificateFieldConfigsLength.size()

    val bitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig] =
      (1 to bitVectorCertificateFieldConfigsLength.value().intValue()).map ( _ => {
        val bitVectorSizeBits: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
        currentOffset += 4
        val maxCompressedSizeBytes: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
        currentOffset += 4
        new CustomBitvectorElementsConfig(bitVectorSizeBits, maxCompressedSizeBytes)
      })

    val ftMinAmount: Long = BytesUtils.getReversedLong(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 8

    val btrFee: Long = BytesUtils.getReversedLong(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 8

    val mainchainBackwardTransferRequestDataLength: Byte = sidechainCreationOutputBytes(currentOffset)
    currentOffset += 1

    MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes.slice(offset, currentOffset),
      version, withdrawalEpochLength, amount, address, customCreationData, constantOpt, certVk,
      ceasedVk, fieldElementCertificateFieldConfigs, bitVectorCertificateFieldConfigs,
      ftMinAmount, btrFee, mainchainBackwardTransferRequestDataLength)
  }
}

class MainchainTxSidechainCreationCrosschainOutput(override val sidechainId: Array[Byte], data: MainchainTxSidechainCreationCrosschainOutputData)
                                                    extends MainchainTxSidechainCreationCrosschainOutputData(
                                                      data.sidechainCreationOutputBytes,
                                                      data.version,
                                                      data.withdrawalEpochLength,
                                                      data.amount,
                                                      data.address,
                                                      data.customCreationData,
                                                      data.constantOpt,
                                                      data.certVk,
                                                      data.ceasedVkOpt,
                                                      data.fieldElementCertificateFieldConfigs,
                                                      data.bitVectorCertificateFieldConfigs,
                                                      data.ftMinAmount,
                                                      data.btrFee,
                                                      data.mainchainBackwardTransferRequestDataLength
                                                    ) with MainchainTxCrosschainOutput {

  override lazy val hash: Array[Byte] = Utils.doubleSHA256Hash(sidechainCreationOutputBytes)
}


object MainchainTxSidechainCreationCrosschainOutput {
  def calculateSidechainId(transactionHash: Array[Byte], index: Int): Array[Byte] = {
    ScCryptoUtils.calculateSidechainId(transactionHash, index)
  }
}

