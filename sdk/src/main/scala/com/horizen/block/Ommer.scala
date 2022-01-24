package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import com.horizen.utils.{BytesUtils, ListSerializer, MerkleTree, Utils}
import com.horizen.validation.{InconsistentOmmerDataException, InvalidOmmerDataException}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}
import scorex.util.idToBytes

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("id"))
case class Ommer(
                  override val header: SidechainBlockHeader,
                  mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]], // Empty if no mainchainBlockReferencesData present in block.
                  override val mainchainHeaders: Seq[MainchainHeader],
                  override val ommers: Seq[Ommer]
                ) extends OmmersContainer with BytesSerializable {
  override type M = Ommer

  override def serializer: ScorexSerializer[Ommer] = OmmerSerializer

  lazy val id: Array[Byte] = idToBytes(header.id)

  def verifyDataConsistency(): Try[Unit] = Try {
    // Verify that Ommers' mainchainReferencesHeaders, ReferencesData and nextMainchainHeaders root hashes are consistent to sidechainBlockHeader.mainchainMerkleRootHash.
    if(mainchainHeaders.isEmpty && mainchainReferencesDataMerkleRootHashOption.isEmpty) {
      if(!header.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} contains inconsistent Mainchain data.")
    } else {
      // Calculate Merkle root hashes of mainchainBlockReferences Data
      val mainchainReferencesDataMerkleRootHash = mainchainReferencesDataMerkleRootHashOption.getOrElse(Utils.ZEROS_HASH)

      // Calculate Merkle root hashes of mainchainHeaders
      val mainchainHeadersMerkleRootHash = if (mainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else {
        val merkleTree = MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava)
        // Check that MerkleTree was not mutated.
        if(merkleTree.isMutated)
          throw new InconsistentOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} MainchainHeaders lead to mutated MerkleTree.")
        merkleTree.rootHash()
      }

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
      // Note: no need to check that MerkleTree is not mutated.
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()

      // Compare final hash with the one stored in SidechainBlockHeader
      if (!header.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} contains inconsistent Mainchain data.")
    }

    if(ommers.isEmpty) {
      if(!header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} contains inconsistent Subommers.")
    } else {
      val merkleTree = MerkleTree.createMerkleTree(ommers.map(_.id).asJava)
      val calculatedMerkleRootHash = merkleTree.rootHash()
      if(!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} contains inconsistent Ommers.")

      // Check that MerkleTree was not mutated.
      if(merkleTree.isMutated)
        throw new InconsistentOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} Ommers lead to mutated MerkleTree.")
    }

    // Check sub ommers data consistency
    for(ommer <- ommers) {
      ommer.verifyDataConsistency() match {
        case Success(_) =>
        case Failure(e) => throw e
      }
    }
  }

  def verifyData(params: NetworkParams): Try[Unit] = Try {
    // Check that header is valid.
    // Even if we got non-critical error like SidechainBlockSlotInFutureException, change it to critical one.
    header.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => throw new InvalidOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} data is invalid: ${e.getMessage}")
    }

    // Verify that each MainchainHeader is semantically valid
    // Even if we got non-critical error like MainchainHeaderTimestampInFutureException, change it to critical one.
    for(mainchainHeader <- mainchainHeaders) {
      mainchainHeader.semanticValidity(params) match {
        case Success(_) =>
        case Failure(e) => throw new InvalidOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} data is invalid: ${e.getMessage}")
      }
    }

    // Verify that MainchainHeaders lead to consistent MC chain
    for(i <- 0 until mainchainHeaders.size - 1) {
      if(!mainchainHeaders(i).isParentOf(mainchainHeaders(i+1)))
        throw new InvalidOmmerDataException(s"Ommer ${BytesUtils.toHexString(id)} MainchainHeader ${mainchainHeaders(i).hashHex} is not a parent of MainchainHeader ${mainchainHeaders(i+1)}.")
    }

    verifyOmmersSeqData(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }
  }

  override def hashCode(): Int = java.util.Arrays.hashCode(id)

  override def equals(obj: Any): Boolean = {
    obj match {
      case ommer: Ommer =>
        id.sameElements(ommer.id) &&
          mainchainHeaders.equals(ommer.mainchainHeaders) &&
          ommers.equals(ommer.ommers) &&
          mainchainReferencesDataMerkleRootHashOption.getOrElse(Array[Byte]())
            .sameElements(ommer.mainchainReferencesDataMerkleRootHashOption.getOrElse(Array[Byte]()))
      case _ => false
    }
  }
}


object Ommer {
  def toOmmer(block: SidechainBlock): Ommer = {
    val mainchainReferencesDataMerkleRootHashOption: Option[Array[Byte]] = {
      val referencesDataHashes: Seq[Array[Byte]] = block.mainchainBlockReferencesData.map(_.headerHash)
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
