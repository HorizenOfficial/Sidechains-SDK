package com.horizen.block

import com.google.common.primitives.Bytes
import com.horizen.params.NetworkParams
import com.horizen.utils.{ListSerializer, MerkleTree, Utils}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Blake2b256
import scorex.util.serialization.{Reader, Writer}
import scorex.util.idToBytes

import scala.collection.JavaConverters._

case class Ommer(
                  sidechainBlockHeader: SidechainBlockHeader,
                  mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]], // Empty if no mainchainBlockReferences present in block.
                  mainchainBlockHeaders: Seq[MainchainHeader] // mainchainBlockReferences.headers + nextMainchainHeaders
                ) extends BytesSerializable {
  override type M = Ommer

  override def serializer: ScorexSerializer[Ommer] = OmmerSerializer

  lazy val id: Array[Byte] = {
    Blake2b256(Bytes.concat(
      idToBytes(sidechainBlockHeader.id),
      mainchainReferencesDataMerkleRootHashOption.getOrElse(Utils.ZEROS_HASH),
      MerkleTree.createMerkleTree(mainchainBlockHeaders.map(_.hash).asJava).rootHash()
    ))
  }

  def semanticValidity(params: NetworkParams): Boolean = {
    if(sidechainBlockHeader == null || mainchainBlockHeaders == null)
      return false

    if(!sidechainBlockHeader.semanticValidity())
      return false

    for(mainchainHeader <- mainchainBlockHeaders)
      if(!mainchainHeader.semanticValidity(params))
        return false

    // Verify that Ommers' mainchainBlockHeaders and ReferencesData root hash are consistent to sidechainBlockHeader.mainchainMerkleRootHash.
    if(mainchainBlockHeaders.isEmpty && mainchainReferencesDataMerkleRootHashOption.isEmpty) {
      if(!sidechainBlockHeader.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
    } else {
      val mainchainReferencesDataMerkleRootHash = mainchainReferencesDataMerkleRootHashOption.getOrElse(Utils.ZEROS_HASH)
      val headersMerkleRootHash = MerkleTree.createMerkleTree(mainchainBlockHeaders.map(_.hash).asJava).rootHash()
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, headersMerkleRootHash).asJava
      ).rootHash()

      if(!sidechainBlockHeader.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        return false
    }
    true
  }
}


object OmmerSerializer extends ScorexSerializer[Ommer] {
  private val mainchainHeaderListSerializer = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)

  override def serialize(obj: Ommer, w: Writer): Unit = {
    SidechainBlockHeaderSerializer.serialize(obj.sidechainBlockHeader, w)
    obj.mainchainReferencesDataMerkleRootHashOption match {
      case Some(rootHash) =>
        w.putInt(rootHash.length)
        w.putBytes(rootHash)
      case None =>
        w.putInt(0)
    }
    mainchainHeaderListSerializer.serialize(obj.mainchainBlockHeaders.asJava, w)
  }

  override def parse(r: Reader): Ommer = {
    val sidechainBlockHeader: SidechainBlockHeader = SidechainBlockHeaderSerializer.parse(r)
    val referencesDataHashLength: Int = r.getInt()
    val mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]] = if(referencesDataHashLength == 0)
      None
    else
      Some(r.getBytes(referencesDataHashLength))

    val mainchainBlockHeaders: Seq[MainchainHeader] = mainchainHeaderListSerializer.parse(r).asScala

    Ommer(sidechainBlockHeader, mainchainReferencesDataMerkleRootHashOption, mainchainBlockHeaders)
  }
}
