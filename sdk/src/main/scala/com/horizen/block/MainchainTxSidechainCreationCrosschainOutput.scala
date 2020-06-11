package com.horizen.block

import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.utils.{BytesUtils, Utils, VarInt}

import scala.util.Try

class MainchainTxSidechainCreationCrosschainOutput(
                                                    val sidechainCreationOutputBytes: Array[Byte],
                                                    override val sidechainId: Array[Byte],
                                                    val withdrawalEpochLength: Int,
                                                    val amount: Long,
                                                    val address: Array[Byte],
                                                    val customData: Array[Byte],
                                                    val constant: Array[Byte],
                                                    val certVk: Array[Byte]
                                                  ) extends MainchainTxCrosschainOutput {
  override val outputType: Byte = MainchainTxSidechainCreationCrosschainOutput.OUTPUT_TYPE

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(sidechainCreationOutputBytes))

  def size: Int = sidechainCreationOutputBytes.length
}


object MainchainTxSidechainCreationCrosschainOutput {
  val OUTPUT_TYPE: Byte = 3.toByte

  def create(sidechainCreationOutputBytes: Array[Byte], offset: Int): Try[MainchainTxSidechainCreationCrosschainOutput] = Try {

    var currentOffset: Int = offset

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(sidechainCreationOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

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

    val constant: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + constantLength.value().intValue())
    currentOffset += constantLength.value().intValue()

    val certVkSize: Int = CryptoLibProvider.sigProofThresholdCircuitFunctions.certVkSize()
    val certVk: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + certVkSize)
    currentOffset += certVkSize

    new MainchainTxSidechainCreationCrosschainOutput(sidechainCreationOutputBytes.slice(offset, currentOffset),
      sidechainId, withdrawalEpochLength, amount, address, customData, constant, certVk)
  }
}
