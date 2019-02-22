package com.horizen.fixtures

import com.horizen.SidechainMemoryPool
import com.horizen.SidechainTypes

import scala.collection.concurrent.TrieMap

trait SidechainMemoryPoolFixture {

  def getSidechainMemoryPool () : SidechainMemoryPool = {
    new SidechainMemoryPool(new TrieMap[String, SidechainTypes#BT]())
  }

}
