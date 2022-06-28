package com.horizen.fixtures

import com.horizen.{SidechainMemoryPool, SidechainMemoryPoolEntry}

import scala.collection.concurrent.TrieMap

trait SidechainMemoryPoolFixture {

  def getSidechainMemoryPool () : SidechainMemoryPool = {
    new SidechainMemoryPool(new TrieMap[String, SidechainMemoryPoolEntry]())
  }

}
