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
                  mainchainReferencesHeaders: Seq[MainchainHeader],
                  nextMainchainHeaders: Seq[MainchainHeader]
                ) extends BytesSerializable {
  override type M = Ommer

  override def serializer: ScorexSerializer[Ommer] = OmmerSerializer

  lazy val id: Array[Byte] = {
    Blake2b256(Bytes.concat(
      idToBytes(sidechainBlockHeader.id),
      mainchainReferencesDataMerkleRootHashOption.getOrElse(Utils.ZEROS_HASH),
      if(mainchainReferencesHeaders.isEmpty) Utils.ZEROS_HASH else MerkleTree.createMerkleTree(mainchainReferencesHeaders.map(_.hash).asJava).rootHash(),
      if(nextMainchainHeaders.isEmpty) Utils.ZEROS_HASH else  MerkleTree.createMerkleTree(nextMainchainHeaders.map(_.hash).asJava).rootHash()
    ))
  }

  def semanticValidity(params: NetworkParams): Boolean = {
    if(sidechainBlockHeader == null || mainchainReferencesHeaders == null || nextMainchainHeaders == null)
      return false

    if(!sidechainBlockHeader.semanticValidity())
      return false

    val ommerMainchainHeaders = mainchainReferencesHeaders ++ nextMainchainHeaders
    // Verify that each MainchainHeader is semantically valid
    for(mainchainHeader <- ommerMainchainHeaders)
      if(!mainchainHeader.semanticValidity(params))
        return false

    // Verify that mainchainReferencesHeaders and nextMainchainHeaders lead to consistent MC chain
    for (i <- 1 until ommerMainchainHeaders.size) {
      if (!ommerMainchainHeaders(i).hasParent(ommerMainchainHeaders(i-1)))
        return false
    }

    // Verify that Ommers' mainchainReferencesHeaders, ReferencesData and nextMainchainHeaders root hashes are consistent to sidechainBlockHeader.mainchainMerkleRootHash.
    if(mainchainReferencesHeaders.isEmpty && nextMainchainHeaders.isEmpty && mainchainReferencesDataMerkleRootHashOption.isEmpty) {
      if(!sidechainBlockHeader.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
    } else {
      // mainchainReferencesDataMerkleRootHashOption must be defined or not according to mainchainReferencesHeaders existence.
      if(mainchainReferencesHeaders.isEmpty != mainchainReferencesDataMerkleRootHashOption.isEmpty)
        return false

      // Calculate Merkle root hashes of mainchainBlockReferences Data
      val mainchainReferencesDataMerkleRootHash = mainchainReferencesDataMerkleRootHashOption.getOrElse(Utils.ZEROS_HASH)

      // Calculate Merkle root hashes of mainchainBlockReferences Headers
      val mainchainReferencesHeadersMerkleRootHash = if (mainchainReferencesHeaders.isEmpty)
        Utils.ZEROS_HASH
      else {
          MerkleTree.createMerkleTree(mainchainReferencesHeaders.map(_.hash).asJava).rootHash()
      }

      // Calculate Merkle root hash of next MainchainHeaders
      val nextMainchainHeadersMerkleRootHash = if (nextMainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else
        MerkleTree.createMerkleTree(nextMainchainHeaders.map(_.hash).asJava).rootHash()

      // Calculate final root hash, that takes as leaves three previously calculated root hashes.
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainReferencesHeadersMerkleRootHash, nextMainchainHeadersMerkleRootHash).asJava
      ).rootHash()

      // Compare final hash with the one stored in SidechainBlockHeader
      if (!sidechainBlockHeader.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        return false
    }

    true
  }

  override def hashCode(): Int = java.util.Arrays.hashCode(id)

  override def equals(obj: Any): Boolean = {
    obj match {
      case ommer: Ommer => id.sameElements(ommer.id)
      case _ => false
    }
  }
}


object Ommer {
  def toOmmer(block: SidechainBlock): Ommer = {
    val mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]] = {
      val referencesDataHashes: Seq[Array[Byte]] = block.mainchainBlockReferences.map(_.dataHash)
      if (referencesDataHashes.isEmpty)
        None
      else
        Some(MerkleTree.createMerkleTree(referencesDataHashes.asJava).rootHash())
    }

    Ommer(
      block.header,
      mainchainReferencesDataMerkleRootHashOption,
      block.mainchainBlockReferences.map(_.header),
      block.nextMainchainHeaders
    )
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
    mainchainHeaderListSerializer.serialize(obj.mainchainReferencesHeaders.asJava, w)
    mainchainHeaderListSerializer.serialize(obj.nextMainchainHeaders.asJava, w)
  }

  override def parse(r: Reader): Ommer = {
    val sidechainBlockHeader: SidechainBlockHeader = SidechainBlockHeaderSerializer.parse(r)
    val referencesDataHashLength: Int = r.getInt()
    val mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]] = if(referencesDataHashLength == 0)
      None
    else
      Some(r.getBytes(referencesDataHashLength))

    val mainchainReferencesHeaders: Seq[MainchainHeader] = mainchainHeaderListSerializer.parse(r).asScala

    val nextMainchainHeaders: Seq[MainchainHeader] = mainchainHeaderListSerializer.parse(r).asScala

    Ommer(sidechainBlockHeader, mainchainReferencesDataMerkleRootHashOption, mainchainReferencesHeaders, nextMainchainHeaders)
  }
}
