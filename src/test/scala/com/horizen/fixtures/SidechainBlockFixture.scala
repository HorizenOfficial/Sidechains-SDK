package com.horizen.fixtures

import java.time.Instant

import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.secret.PrivateKey25519Creator
import scorex.util.{ModifierId, bytesToId}

import scala.util.{Failure, Success}

trait SidechainBlockFixture {

  def generateGenesisBlock(companion: SidechainTransactionsCompanion, basicSeed: Long = 6543211L): SidechainBlock = {
    SidechainBlock.create(
      bytesToId(new Array[Byte](32)),
      Instant.now.getEpochSecond - 10000,
      Seq(),
      Seq(),
      PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(basicSeed).getBytes),
      companion,
      null
    ).get
  }

  def generateSidechainBlockSeq(count: Int, companion: SidechainTransactionsCompanion, params: NetworkParams, basicSeed: Long = 65432L): Seq[SidechainBlock] = {
    var res: Seq[SidechainBlock] = Seq()

    for(i <- 0 until count) {
      val parentId: ModifierId = {
        if (i == 0)
          params.sidechainGenesisBlockId
        else
          res(i - 1).id
      }
      res = res :+ SidechainBlock.create(
        parentId,
        Instant.now.getEpochSecond - 1000 + i * 10,
        Seq(),
        Seq(),
        PrivateKey25519Creator.getInstance().generateSecret("seed%d".format(basicSeed.toInt + i).getBytes),
        companion,
        params
      ).get
    }
    res
  }

  def generateNextSidechainBlock(sidechainBlock: SidechainBlock, companion: SidechainTransactionsCompanion, params: NetworkParams, basicSeed: Long = 123177L): SidechainBlock = {
    SidechainBlock.create(
      sidechainBlock.id,
      sidechainBlock.timestamp + 10,
      Seq(),
      Seq(),
      PrivateKey25519Creator.getInstance().generateSecret("seed%d".format(basicSeed).getBytes),
      companion,
      params
    ).get
  }
}
