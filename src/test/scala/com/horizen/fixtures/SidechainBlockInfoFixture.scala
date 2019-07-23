package com.horizen.fixtures

import com.horizen.chain.SidechainBlockInfo
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId}

import scala.collection.mutable.ArrayBuffer

trait SidechainBlockInfoFixture {

  def getRandomModifier(): ModifierId = {
    val parentBytes: Array[Byte] = new Array[Byte](32)
    util.Random.nextBytes(parentBytes)
    bytesToId(parentBytes)
  }


  def getRandomModifier(seed: Long): ModifierId = {
    util.Random.setSeed(seed)
    getRandomModifier()
  }

  def getRandomModifiersSeq(count: Int): Seq[ModifierId] = {
    var modifiers: Seq[ModifierId] = Seq()
    for(i <- 0 until count)
      modifiers = modifiers :+ getRandomModifier()
    modifiers
  }

  def getRandomModifiersSeq(count: Int, basicSeed: Long): Seq[ModifierId] = {
    util.Random.setSeed(basicSeed)
    getRandomModifiersSeq(count)
  }

  def generateBlockInfoData(count: Int, basicSeed: Long = 112345L): ArrayBuffer[(ModifierId, SidechainBlockInfo)] = {
    util.Random.setSeed(basicSeed)

    var id: ModifierId = getRandomModifier()
    var height: Int = 1
    var score: Long = 1L
    val validity: ModifierSemanticValidity = ModifierSemanticValidity.Valid

    val res: ArrayBuffer[(ModifierId, SidechainBlockInfo)] = ArrayBuffer()
    for(i <- 0 until count) {
      val info = SidechainBlockInfo(height, score, id, validity)
      id = getRandomModifier()
      res.append(id -> info)
      height += 1
      score += 1L
    }
    res
  }
}
