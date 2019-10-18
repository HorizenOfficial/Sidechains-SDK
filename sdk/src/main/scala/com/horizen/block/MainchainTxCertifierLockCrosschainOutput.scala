package com.horizen.block

import com.horizen.utils.{BytesUtils, Utils}

import scala.util.Try


class MainchainTxCertifierLockCrosschainOutput(
                                      val certifierLockOutputBytes: Array[Byte],
                                      override val sidechainId: Array[Byte],
                                      val lockedAmount: Long,
                                      val propositionBytes: Array[Byte],
                                      val activeFromWithdrawalEpoch: Long
                                    ) extends MainchainTxCrosschainOutput {
  override val outputType: Byte = MainchainTxCertifierLockCrosschainOutput.OUTPUT_TYPE

  override lazy val hash: Array[Byte] = BytesUtils.reverseBytes(Utils.doubleSHA256Hash(certifierLockOutputBytes))
}

object MainchainTxCertifierLockCrosschainOutput {
  val OUTPUT_TYPE: Byte = 2.toByte
  val CERTIFIER_LOCK_OUTPUT_SIZE = 80 // 8 + 32 + 32 + 8

  def create(certifierLockOutputBytes: Array[Byte], offset: Int): Try[MainchainTxCertifierLockCrosschainOutput] = Try {
    if(offset < 0 || certifierLockOutputBytes.length - offset < CERTIFIER_LOCK_OUTPUT_SIZE)
      throw new IllegalArgumentException("Input data corrupted.")

    var currentOffset: Int = offset

    val lockedAmount: Long = BytesUtils.getReversedLong(certifierLockOutputBytes, currentOffset)
    currentOffset += 8

    val propositionBytes: Array[Byte] = BytesUtils.reverseBytes(certifierLockOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val sidechainId: Array[Byte] = BytesUtils.reverseBytes(certifierLockOutputBytes.slice(currentOffset, currentOffset + 32))
    currentOffset += 32

    val activeFromWithdrawalEpoch: Long = BytesUtils.getReversedLong(certifierLockOutputBytes, currentOffset)
    currentOffset += 8

    new MainchainTxCertifierLockCrosschainOutput(certifierLockOutputBytes.slice(offset, currentOffset), sidechainId, lockedAmount, propositionBytes, activeFromWithdrawalEpoch)
  }
}