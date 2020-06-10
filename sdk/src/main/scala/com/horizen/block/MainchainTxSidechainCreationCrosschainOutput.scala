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
                                                       val certVk: Array[Byte]) {
  def size: Int = sidechainCreationOutputBytes.length
}

object MainchainTxSidechainCreationCrosschainOutputData {
  val OUTPUT_TYPE: Byte = 3.toByte
  //val SIDECHAIN_CREATION_OUTPUT_SIZE = 36 // 32 + 4

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

    val constantLength: Int = CryptoLibProvider.sigProofThresholdCircuitFunctions.sysDataConstantLength()
    val constant: Array[Byte] = new Array[Byte](constantLength)
    //val constant: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + constantLength)
    //currentOffset += constantLength

    val certVkSize: Int = 1544 // TODO: take it from zendoo interface
    val certVk: Array[Byte] = new Array[Byte](certVkSize) // TODO: uncomment when possible
    //val certVk: Array[Byte] = sidechainCreationOutputBytes.slice(currentOffset, currentOffset + certVkSize)
    //currentOffset += certVkSize

    new MainchainTxSidechainCreationCrosschainOutputData(sidechainCreationOutputBytes.slice(offset, currentOffset),
      withdrawalEpochLength, amount, address, customData, constant, certVk)
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
                                                      data.certVk
                                                    ) with MainchainTxCrosschainOutput {
  override val outputType: Byte = MainchainTxSidechainCreationCrosschainOutput.OUTPUT_TYPE

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(sidechainCreationOutputBytes))
}


object MainchainTxSidechainCreationCrosschainOutput {
  val OUTPUT_TYPE: Byte = 3.toByte

  def calculateSidechainId(index: Int, transactionHash: Array[Byte]): Array[Byte] = {
    BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(transactionHash), BytesUtils.reverseBytes(Ints.toByteArray(index))))
  }
}

