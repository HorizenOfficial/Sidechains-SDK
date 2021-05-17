package com.horizen.block

import com.google.common.primitives.Ints
import com.horizen.commitmenttree.{CustomBitvectorElementsConfig, CustomFieldElementsConfig}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.utils.{BytesUtils, Utils, VarInt}

import scala.util.Try

case class MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes: Array[Byte],
                                                            withdrawalEpochLength: Int,
                                                            amount: Long,
                                                            address: Array[Byte],
                                                            customCreationData: Array[Byte],
                                                            constantOpt: Option[Array[Byte]],
                                                            certVk: Array[Byte],
                                                            ceasedVkOpt: Option[Array[Byte]],
                                                            mainchainBackwardTransferRequestDataLength: Byte,
                                                            fieldElementCertificateFieldConfigs: Seq[CustomFieldElementsConfig],
                                                            bitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig],
                                                            ftMinAmount: Long,
                                                            btrFee: Long) {
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

    val customCreationDataLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
    currentOffset += customCreationDataLength.size()

    val customCreationData: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + customCreationDataLength.value().intValue())
    currentOffset += customCreationDataLength.value().intValue()

    val constantPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val constantOpt: Option[Array[Byte]] = if(constantPresence) {
      val constantLength: VarInt = BytesUtils.getReversedVarInt(sidechainCreationOutputBytes, currentOffset)
      currentOffset += constantLength.size()
      val constant = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + constantLength.value().intValue())
      currentOffset += constantLength.value().intValue()
      Some(constant)
    } else {
      None
    }

    // TODO: Vk will have variable size
    val vkSize: Int = CryptoLibProvider.sigProofThresholdCircuitFunctions.certVkSize()
    val certVk: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + vkSize)
    currentOffset += vkSize

    // TODO: Vk will have variable size
    val ceasedVkPresence: Boolean = sidechainCreationOutputBytes(currentOffset) == 1
    currentOffset += 1
    val ceasedVk: Option[Array[Byte]] = if(ceasedVkPresence) {
      Some(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + vkSize))
    } else {
      None
    }
    if(ceasedVkPresence) currentOffset += vkSize

    // TODO: check fields order serialization in MC
    val mainchainBackwardTransferRequestDataLength: Byte = sidechainCreationOutputBytes(currentOffset)
    currentOffset += 1


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

    MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes.slice(offset, currentOffset),
      withdrawalEpochLength, amount, address, customCreationData, constantOpt, certVk,
      ceasedVk, mainchainBackwardTransferRequestDataLength, fieldElementCertificateFieldConfigs,
      bitVectorCertificateFieldConfigs, ftMinAmount, btrFee)
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
                                                      data.mainchainBackwardTransferRequestDataLength,
                                                      data.fieldElementCertificateFieldConfigs,
                                                      data.bitVectorCertificateFieldConfigs,
                                                      data.ftMinAmount,
                                                      data.btrFee
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

