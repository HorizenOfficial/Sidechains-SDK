package com.horizen.block

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.librustsidechains.FieldElement
import com.horizen.serialization.ReverseBytesSerializer
import com.horizen.utils.{BytesUtils, Utils, VarInt}

import scala.util.Try

case class MainchainTxCswCrosschainInput(cswInputBytes: Array[Byte],
                                         amount: Long,                                        // CAmount (int64_t)
                                         @JsonSerialize(using = classOf[ReverseBytesSerializer]) sidechainId: Array[Byte], // uint256
                                         nullifier: Array[Byte],                              // CFieldElement
                                         @JsonSerialize(using = classOf[ReverseBytesSerializer]) mcPubKeyHash: Array[Byte], // uint160
                                         scProof: Array[Byte],                                // ScProof
                                         actCertDataHashOpt: Option[Array[Byte]],             // CFieldElement
                                         ceasingCumulativeScTxCommitmentTreeRoot: Array[Byte],// CFieldElement
                                         @JsonSerialize(using = classOf[ReverseBytesSerializer]) redeemScript: Array[Byte] // CScript
                                        ) {

  lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(cswInputBytes))

  def size: Int = cswInputBytes.length
}


object MainchainTxCswCrosschainInput {
  def create(cswInputBytes: Array[Byte], offset: Int): Try[MainchainTxCswCrosschainInput] = Try {
    if(offset < 0)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val amount: Long = BytesUtils.getReversedLong(cswInputBytes, currentOffset)
    currentOffset += 8

    val sidechainId: Array[Byte] = cswInputBytes.slice(currentOffset, currentOffset + 32)
    currentOffset += 32

    val nullifierSize: VarInt = BytesUtils.getReversedVarInt(cswInputBytes, currentOffset)
    currentOffset += nullifierSize.size()
    if(nullifierSize.value() != FieldElementUtils.fieldElementLength())
      throw new IllegalArgumentException(s"Input data corrupted: nullifier size ${nullifierSize.value()} " +
        s"is expected to be FieldElement size ${FieldElementUtils.fieldElementLength()}")
    val nullifier: Array[Byte] = cswInputBytes.slice(currentOffset, currentOffset + nullifierSize.value().intValue())
    currentOffset += nullifierSize.value().intValue()

    val mcPubKeyHash: Array[Byte] = cswInputBytes.slice(currentOffset, currentOffset + 20)
    currentOffset += 20

    val scProofSize: VarInt = BytesUtils.getReversedVarInt(cswInputBytes, currentOffset)
    currentOffset += scProofSize.size()

    val scProof: Array[Byte] = cswInputBytes.slice(currentOffset, currentOffset + scProofSize.value().intValue())
    currentOffset += scProofSize.value().intValue()

    val actCertDataHashSize: VarInt = BytesUtils.getReversedVarInt(cswInputBytes, currentOffset)
    currentOffset += actCertDataHashSize.size()

    // Note: There are two valid cases for actCertDataHash: to be null or to have a FE size
    // Null case is for ceased sidechains without active certificates.
    val actCertDataHashOpt: Option[Array[Byte]] = if(actCertDataHashSize.value() == 0) {
      None
    } else {
      if (actCertDataHashSize.value() != FieldElementUtils.fieldElementLength())
        throw new IllegalArgumentException(s"Input data corrupted: actCertDataHash size ${actCertDataHashSize.value()} " +
          s"is expected to be FieldElement size ${FieldElementUtils.fieldElementLength()}")

      val actCertDataHash: Array[Byte] = cswInputBytes.slice(currentOffset, currentOffset + actCertDataHashSize.value().intValue())
      currentOffset += actCertDataHashSize.value().intValue()

      Some(actCertDataHash)
    }

    val ceasingCumulativeScTxCommitmentTreeRootSize: VarInt = BytesUtils.getReversedVarInt(cswInputBytes, currentOffset)
    currentOffset += ceasingCumulativeScTxCommitmentTreeRootSize.size()

    if(ceasingCumulativeScTxCommitmentTreeRootSize.value() != FieldElementUtils.fieldElementLength())
      throw new IllegalArgumentException(s"Input data corrupted: ceasingCumulativeScTxCommitmentTreeRoot size ${ceasingCumulativeScTxCommitmentTreeRootSize.value()} " +
        s"is expected to be FieldElement size ${FieldElementUtils.fieldElementLength()}")
    val ceasingCumulativeScTxCommitmentTreeRoot: Array[Byte] = cswInputBytes.slice(currentOffset, currentOffset + ceasingCumulativeScTxCommitmentTreeRootSize.value().intValue())
    currentOffset += ceasingCumulativeScTxCommitmentTreeRootSize.value().intValue()

    val scriptLength: VarInt = BytesUtils.getReversedVarInt(cswInputBytes, currentOffset)
    currentOffset += scriptLength.size()

    val redeemScript: Array[Byte] = cswInputBytes.slice(currentOffset, currentOffset + scriptLength.value().intValue())
    currentOffset += scriptLength.value().intValue()


    new MainchainTxCswCrosschainInput(cswInputBytes.slice(offset, currentOffset),
      amount, sidechainId, nullifier, mcPubKeyHash, scProof, actCertDataHashOpt,
      ceasingCumulativeScTxCommitmentTreeRoot, redeemScript)
  }
}