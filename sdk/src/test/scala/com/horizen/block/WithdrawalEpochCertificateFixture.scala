package com.horizen.block

import java.util.Random

trait WithdrawalEpochCertificateFixture {
  private def getBytes(len: Int = 32, rnd: Random = new Random()): Array[Byte] = {
    val bytes = new Array[Byte](len)
    rnd.nextBytes(bytes)
    bytes
  }

  def generateWithdrawalEpochCertificate(previousMcBlockHashOpt: Option[Array[Byte]] = None, rnd: Random = new Random(), epoch: Option[Int] = None): WithdrawalEpochCertificate = {
    val epochNumber = epoch.getOrElse(rnd.nextInt())
    WithdrawalEpochCertificate(
      getBytes(),
      rnd.nextInt,
      getBytes(32),
      epochNumber,
      rnd.nextLong(),
      getBytes(),
      Seq(),
      Seq(),
      previousMcBlockHashOpt.getOrElse(getBytes()), // TODO: review this argument value definition.
      rnd.nextLong(),
      rnd.nextLong(),
      Seq(),
      Seq(),
      Seq())
  }
}