package com.horizen.chain

trait ChainData[T] {
  def getParentId: T
}
