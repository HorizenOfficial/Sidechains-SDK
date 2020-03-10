package com.horizen.block

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.SidechainTypes
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, MerkleTree, Utils}

import scala.collection.JavaConverters._
import scala.collection.mutable

case class SidechainHashes
  (hashes: mutable.Map[ByteArrayWrapper, mutable.ListBuffer[Array[Byte]]])
{
  def addHashes(sidechainId: Array[Byte], hashesSeq: Seq[Array[Byte]]): SidechainHashes = {
    val id = new ByteArrayWrapper(sidechainId)
    addHashes(id, hashesSeq)
  }

  def addHashes(sidechainId: ByteArrayWrapper, hashesSeq: Seq[Array[Byte]]): SidechainHashes = {
    hashes.get(sidechainId) match {
      case Some(hs) => hs.appendAll(hashesSeq)
      case None =>
        val hs = new mutable.ListBuffer[Array[Byte]]()
        hs.appendAll(hashesSeq)
        hashes.put(sidechainId, hs)
    }
    this
  }

  def addTransactionOutputs(sidechainId: ByteArrayWrapper,
                            outputs: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]]): SidechainHashes = {
    addHashes(sidechainId, outputs.map(_.hash()))
  }

  def addCertificate(certificate: MainchainBackwardTransferCertificate): SidechainHashes = {
    var index: Int = -1
    addHashes(certificate.sidechainId,
      certificate.outputs.map( o => {
        index += 1
        BytesUtils.reverseBytes(
          Utils.doubleSHA256Hash(
            Bytes.concat(
              BytesUtils.reverseBytes(o.hash),
              BytesUtils.reverseBytes(certificate.hash),
              BytesUtils.reverseBytes(Ints.toByteArray(index))
            )
          )
        )
      })
    )
  }

  def getScMap : mutable.Map[ByteArrayWrapper, Array[Byte]] = {
    hashes.map{
      case (k, v) => (k, MerkleTree.createMerkleTree(v.asJava).rootHash)
    }
  }

  def getMerkleRoot(sidechainId: ByteArrayWrapper): Array[Byte] = {
    hashes.get(sidechainId) match {
      case Some(h) => MerkleTree.createMerkleTree(h.asJava).rootHash()
      case None => Array.emptyByteArray
    }
  }

  def getMerkleRoot(sidechainId: Array[Byte]): Array[Byte] =
    getMerkleRoot(new ByteArrayWrapper(sidechainId))

}

object SidechainHashes {

  def empty: SidechainHashes =
    SidechainHashes(mutable.Map[ByteArrayWrapper, mutable.ListBuffer[Array[Byte]]]())
}
