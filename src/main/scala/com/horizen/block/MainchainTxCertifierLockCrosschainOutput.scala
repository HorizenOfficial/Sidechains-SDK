package com.horizen.block

import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.utils.{BytesUtils, Utils}

import scala.util.Try


class MainchainTxCertifierLockCrosschainOutput(
                                      val certifierLockOutputBytes: Array[Byte],
                                      override val amount: Long,
                                      override val proposition: PublicKey25519Proposition,
                                      override val sidechainId: Array[Byte],
                                      val activeFromWithdrawalEpoch: Long
                                    ) extends MainchainTxCrosschainOutput {
  override val outputType: Byte = MainchainTxCertifierLockCrosschainOutput.OUTPUT_TYPE

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(certifierLockOutputBytes))
}

object MainchainTxCertifierLockCrosschainOutput {
  val OUTPUT_TYPE: Byte = 2.toByte
  val CERTIFIER_LOCK_OUTPUT_SIZE = 81 // 1 + 8 + 32 + 32 + 8

  def create(certifierLockOutputBytes: Array[Byte], offset: Int): Try[MainchainTxCertifierLockCrosschainOutput] = Try {
    if(offset < 0 || certifierLockOutputBytes.length - offset < CERTIFIER_LOCK_OUTPUT_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val outputType: Byte = certifierLockOutputBytes(currentOffset)
    currentOffset += 1
    if(outputType != OUTPUT_TYPE)
      throw new IllegalArgumentException("Input data corrupted. Different CrosschainOutput Type passed.")

    val amount: Long = BytesUtils.getReversedLong(certifierLockOutputBytes, currentOffset)
    currentOffset += 8

    val proposition: PublicKey25519Proposition = new PublicKey25519Proposition(BytesUtils.reverseBytes(certifierLockOutputBytes.slice(currentOffset, currentOffset + 32)))
    currentOffset += 32

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(certifierLockOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val activeFromWithdrawalEpoch: Long = BytesUtils.getReversedLong(certifierLockOutputBytes, currentOffset)
    currentOffset += 8

    new MainchainTxCertifierLockCrosschainOutput(certifierLockOutputBytes.slice(offset, currentOffset), amount, proposition, sidechainId, activeFromWithdrawalEpoch)
  }
}

