package com.horizen.fixtures

import com.google.common.primitives.Longs
import com.horizen.cryptolibprovider.FieldElementUtils
import com.horizen.utils.{ForwardTransferCswData, UtxoCswData}

import scala.util.Random

trait CswDataFixture extends BoxFixture {
  def getUtxoCswData(seed: Long): UtxoCswData = {
    val random: Random = new Random(seed)

    val spendingPubKey = getPrivateKey25519(Longs.toByteArray(seed)).publicImage().bytes()
    val randomCustomFieldsHash = new Array[Byte](32)
    random.nextBytes(randomCustomFieldsHash)

    val randomMerklePath = new Array[Byte](100)
    random.nextBytes(randomMerklePath)

    UtxoCswData(
      getRandomBoxId(seed),
      spendingPubKey,
      random.nextLong() % 1000,
      random.nextLong() % 1000,
      randomCustomFieldsHash,
      randomMerklePath
    )
  }

  def getForwardTransferCswData(seed: Long): ForwardTransferCswData = {
    val random: Random = new Random(seed)

    val receivedPubKey = new Array[Byte](32)
    random.nextBytes(receivedPubKey)

    val mcReturnAddress = new Array[Byte](20)
    random.nextBytes(mcReturnAddress)

    val mcTxHash = new Array[Byte](32)
    random.nextBytes(mcTxHash)

    val scCommitmentMerklePath = new Array[Byte](100)
    random.nextBytes(scCommitmentMerklePath)

    val btrCommitment = FieldElementUtils.randomFieldElementBytes(random.nextLong())
    val certCommitment = FieldElementUtils.randomFieldElementBytes(random.nextLong())
    val scCrCommitment = FieldElementUtils.randomFieldElementBytes(random.nextLong())

    val ftMerklePath = new Array[Byte](100)
    random.nextBytes(ftMerklePath)

    ForwardTransferCswData(
      getRandomBoxId(seed),
      random.nextLong() % 1000L,
      receivedPubKey,
      mcReturnAddress,
      mcTxHash,
      random.nextInt() % 100,
      scCommitmentMerklePath,
      btrCommitment,
      certCommitment,
      scCrCommitment,
      ftMerklePath
    )
  }
}
