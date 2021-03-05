package com.horizen.block

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.utils.{BytesUtils, Utils, VarInt}

import scala.util.Try

class MainchainTxSidechainCreationCrosschainOutputData(val sidechainCreationOutputBytes: Array[Byte],
                                                       val withdrawalEpochLength: Int,
                                                       val amount: Long,
                                                       val address: Array[Byte],
                                                       val customData: Array[Byte],
                                                       val constant: Array[Byte],
                                                       val certVk: Array[Byte],
                                                       val mbtrVk: Option[Array[Byte]],
                                                       val ceasedVk: Option[Array[Byte]]) {
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

    val constant: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + constantLength.value().intValue())
    currentOffset += constantLength.value().intValue()

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


    new MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes.slice(offset, currentOffset),
      withdrawalEpochLength, amount, address, customData, constant, certVk, mbtrVk, ceasedVk)
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
                                                      data.ceasedVk
                                                    ) with MainchainTxCrosschainOutput {

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(sidechainCreationOutputBytes))
}


object MainchainTxSidechainCreationCrosschainOutput {
  def calculateSidechainId(transactionHash: Array[Byte], index: Int): Array[Byte] = {
    BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(transactionHash), BytesUtils.reverseBytes(Ints.toByteArray(index))))
  }
}

