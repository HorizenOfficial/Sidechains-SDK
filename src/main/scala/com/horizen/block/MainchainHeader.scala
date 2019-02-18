package com.horizen.block


/**
  * Basic trait for all Mainchain objects
  * It can provide basic serialization, parsing interfaces and so on
  */
trait MainchainObject {

}


/**
  * Basic representation of MC header
  */
class MainchainHeader(val header: Array[Byte]) extends MainchainObject {
  def version: Int = ???

  def blockHash: Array[Byte] = ???

  def previousBlockHash: Array[Byte] = ???

  def merkleRoot: Array[Byte] = ???

  def timestamp: Int = ???

  def difficultyTarget: Int = ???

  def nonce: Int = ???

  // Expect such data from MC after update.
  def sidechainsMerkleRoots: Map[Integer, Array[Byte]] = ???

  def bytes: Array[Byte] = ???
  //...
}
