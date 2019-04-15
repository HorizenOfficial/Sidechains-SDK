package com.horizen.fixtures

import com.horizen.block.MainchainHeader

// Just for PoW verification testing
case class MainchainHeaderForPoWTest(override val bits: Int, val precalculatedHash: Array[Byte]
) extends MainchainHeader(null, 0, null,
  null, null, null, 0, bits, null, null) {
  override lazy val hash = precalculatedHash
}


trait MainchainHeaderFixture {
  def getHeaderWithPoW(bits: Int, hash: Array[Byte]) : MainchainHeaderForPoWTest = {
    MainchainHeaderForPoWTest(bits, hash)
  }
}
