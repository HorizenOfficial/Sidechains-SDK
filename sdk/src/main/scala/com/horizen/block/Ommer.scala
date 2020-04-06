package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.google.common.primitives.Bytes
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.utils.{ListSerializer, MerkleTree, Utils}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Blake2b256
import scorex.util.serialization.{Reader, Writer}
import scorex.util.idToBytes

import scala.collection.JavaConverters._

@JsonView(Array(classOf[Views.Default]))
case class Ommer(
                  override val header: SidechainBlockHeader,
                  mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]], // Empty if no mainchainBlockReferencesData present in block.
                  override val mainchainHeaders: Seq[MainchainHeader],
                  override val ommers: Seq[Ommer]
                ) extends OmmersContainer with BytesSerializable {
  override type M = Ommer

  override def serializer: ScorexSerializer[Ommer] = OmmerSerializer

  lazy val id: Array[Byte] = {
    Blake2b256(Bytes.concat(
      idToBytes(header.id),
      mainchainReferencesDataMerkleRootHashOption.getOrElse(Utils.ZEROS_HASH),
      if(mainchainHeaders.isEmpty) Utils.ZEROS_HASH else MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava).rootHash(),
      if(ommers.isEmpty) Utils.ZEROS_HASH else  MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    ))
  }

  def semanticValidity(params: NetworkParams): Boolean = {
    if(header == null || mainchainHeaders == null || ommers == null)
      return false

    if(!header.semanticValidity())
      return false

    // Verify that each MainchainHeader is semantically valid
    for(mainchainHeader <- mainchainHeaders)
      if(!mainchainHeader.semanticValidity(params))
        return false

    // Verify that MainchainHeaders lead to consistent MC chain
    for (i <- 0 until mainchainHeaders.size - 1) {
      if (!mainchainHeaders(i).isParentOf(mainchainHeaders(i+1)))
        return false
    }

    // Verify that Ommers' mainchainReferencesHeaders, ReferencesData and nextMainchainHeaders root hashes are consistent to sidechainBlockHeader.mainchainMerkleRootHash.
    if(mainchainHeaders.isEmpty && mainchainReferencesDataMerkleRootHashOption.isEmpty) {
      if(!header.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
    } else {
      // Calculate Merkle root hashes of mainchainBlockReferences Data
      val mainchainReferencesDataMerkleRootHash = mainchainReferencesDataMerkleRootHashOption.getOrElse(Utils.ZEROS_HASH)

      // Calculate Merkle root hashes of mainchainHeaders
      val mainchainHeadersMerkleRootHash = if (mainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else {
          MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava).rootHash()
      }

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()

      // Compare final hash with the one stored in SidechainBlockHeader
      if (!header.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        return false
    }

    verifyOmmers(params)
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
      val referencesDataHashes: Seq[Array[Byte]] = block.mainchainBlockReferencesData.map(_.hash)
      if (referencesDataHashes.isEmpty)
        None
      else
        Some(MerkleTree.createMerkleTree(referencesDataHashes.asJava).rootHash())
    }

    Ommer(
      block.header,
      mainchainReferencesDataMerkleRootHashOption,
      block.mainchainHeaders,
      block.ommers
    )
  }
}


object OmmerSerializer extends ScorexSerializer[Ommer] {
  private val mainchainHeaderListSerializer = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)
  private val ommersListSerializer = new ListSerializer[Ommer](OmmerSerializer)

  override def serialize(obj: Ommer, w: Writer): Unit = {
    SidechainBlockHeaderSerializer.serialize(obj.header, w)
    obj.mainchainReferencesDataMerkleRootHashOption match {
      case Some(rootHash) =>
        w.putInt(rootHash.length)
        w.putBytes(rootHash)
      case None =>
        w.putInt(0)
    }
    mainchainHeaderListSerializer.serialize(obj.mainchainHeaders.asJava, w)
    ommersListSerializer.serialize(obj.ommers.asJava, w)
  }

  override def parse(r: Reader): Ommer = {
    val header: SidechainBlockHeader = SidechainBlockHeaderSerializer.parse(r)
    val referencesDataHashLength: Int = r.getInt()
    val mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]] = if(referencesDataHashLength == 0)
      None
    else
      Some(r.getBytes(referencesDataHashLength))

    val mainchainHeaders: Seq[MainchainHeader] = mainchainHeaderListSerializer.parse(r).asScala

    val ommers: Seq[Ommer] = ommersListSerializer.parse(r).asScala

    Ommer(header, mainchainReferencesDataMerkleRootHashOption, mainchainHeaders, ommers)
  }
}
