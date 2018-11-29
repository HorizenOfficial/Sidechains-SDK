
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

  //...
}


/**
  * Basic representation of MC transaction
  */
class MainchainTransaction(val transaction: Array[Byte]) extends MainchainObject {
  def inputs: Seq[MainchainTxInput] = ???

  def outputs: Seq[MainchainTxOutput] = ???

  def hash: Array[Byte] = ???

  // not necessary
  def sidechainRelatedOutputs(sidechainId: Int): Seq[MainchainTxOutput] = ???
}


/**
  * Basic representation of MC transaction input
  * TODO: do we actually need to parse inputs and get some data from them?
  */
class MainchainTxInput(val input: Array[Byte]) extends MainchainObject {
}


/**
  * Basic representation of MC transaction output
  */
class MainchainTxOutput(val output: Array[Byte]) extends MainchainObject {
  def value: Int = ???

  // not necessary
  def isSidechainRelated(sidechainId: Int): Boolean = ???
}


/**
  * Contains mainchain transaction merkle path
  * Think: maybe we can do it as a part of MainchainTransaction.
  */
case class MainchainTrMerklePath(merklePath: Seq[Array[Byte]]) extends MainchainObject {


}