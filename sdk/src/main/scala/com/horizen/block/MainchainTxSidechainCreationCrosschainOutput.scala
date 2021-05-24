package com.horizen.block

import com.google.common.primitives.Ints
import com.horizen.commitmenttree.{CustomBitvectorElementsConfig, CustomFieldElementsConfig}
import com.horizen.utils.{BytesUtils, Utils, VarInt}
import com.horizen.librustsidechains.{ Utils => ScCryptoUtils }

import scala.util.Try

case class MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes: Array[Byte],
                                                            withdrawalEpochLength: Int,
                                                            amount: Long,
                                                            address: Array[Byte],
                                                            customCreationData: Array[Byte],
                                                            constantOpt: Option[Array[Byte]],
                                                            certVk: Array[Byte],
                                                            ceasedVkOpt: Option[Array[Byte]],
                                                            fieldElementCertificateFieldConfigs: Seq[CustomFieldElementsConfig],
                                                            bitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig],
                                                            ftMinAmount: Long,
                                                            btrFee: Long,
                                                            mainchainBackwardTransferRequestDataLength: Byte) {
  def size: Int = sidechainCreationOutputBytes.length
}

object MainchainTxSidechainCreationCrosschainOutputData {

  def create(sidechainCreationOutputBytes: Array[Byte], offset: Int): Try[MainchainTxSidechainCreationCrosschainOutputData] = Try {

    var currentOffset: Int = offset

    val withdrawalEpochLength: Int = BytesUtils.getReversedInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 4

    val amount: Long = BytesUtils.getReversedLong(sidechainCreationOutputBytes, currentOffset)
    currentOffset += 8

    val address: Array[Byte] = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val customCreationDataLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += customCreationDataLength.size()

    val customCreationData: Array[Byte] = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + customCreationDataLength.value().intValue()))
    currentOffset += customCreationDataLength.value().intValue()

    val constantPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val constantOpt: Option[Array[Byte]] = if(constantPresence) {
      val constantLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
      currentOffset += constantLength.size()
      val constant = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + constantLength.value().intValue()))
      currentOffset += constantLength.value().intValue()
      Some(constant)
    } else {
      None
    }

    val certVkSize: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += certVkSize.size()
    val certVk: Array[Byte] = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + certVkSize.value().intValue()))
    currentOffset += certVkSize.value().intValue()

    val ceasedVkPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val ceasedVk: Option[Array[Byte]] = if(ceasedVkPresence) {
      val ceasedVkSize: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
      currentOffset += ceasedVkSize.size()
      val ceasedVk = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + ceasedVkSize.value().intValue()))
      currentOffset += ceasedVkSize.value().intValue()
      Some(ceasedVk)
    } else {
      None
    }

    val fieldElementCertificateFieldConfigsLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += fieldElementCertificateFieldConfigsLength.size()

    val fieldElementCertificateFieldConfigs: Seq[CustomFieldElementsConfig] =
      (1 to fieldElementCertificateFieldConfigsLength.value().intValue()).map ( _ => {
        val nBits: Byte = sidechainCreationOutputBytes(currentOffset)
        currentOffset += 1
        new CustomFieldElementsConfig(nBits)
      })

    val bitVectorCertificateFieldConfigsLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
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
      withdrawalEpochLength, amount, address, customCreationData, constantOpt, certVk,
      ceasedVk, fieldElementCertificateFieldConfigs, bitVectorCertificateFieldConfigs,
      ftMinAmount, btrFee, mainchainBackwardTransferRequestDataLength)
  }
}

class MainchainTxSidechainCreationCrosschainOutput(override val sidechainId: Array[Byte], data: MainchainTxSidechainCreationCrosschainOutputData)
                                                    extends MainchainTxSidechainCreationCrosschainOutputData(
                                                      data.sidechainCreationOutputBytes,
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

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(sidechainCreationOutputBytes))
}


object MainchainTxSidechainCreationCrosschainOutput {
  def calculateSidechainId(transactionHash: Array[Byte], index: Int): Array[Byte] = {
    // Note: sc-cryptolib returns the fe bytes in LE, but we keep it in BE as the data returned from MC RPC.
    BytesUtils.reverseBytes(
      ScCryptoUtils.calculateSidechainId(
        BytesUtils.reverseBytes(transactionHash),
        BytesUtils.getReversedInt(Ints.toByteArray(index), 0) // LE integer
      )
    )
  }
}

