package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.proof.Signature25519
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.{ScorexModifierIdSerializer, Views}
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.{ListSerializer, MerklePath, MerkleTree, Utils}
import com.horizen.vrf.VRFProof
import com.horizen.{ScorexEncoding, SidechainTypes}
import scorex.core.block.Block
import scorex.core.block.Block.Timestamp
import scorex.core.serialization.ScorexSerializer
import scorex.core.{ModifierTypeId, idToBytes}
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._
import scala.util.Try

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "transactions", "version", "serializer", "modifierTypeId", "encoder", "companion"))
class SidechainBlock(
                      val header: SidechainBlockHeader,
                      val sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
                      val mainchainBlockReferences: Seq[MainchainBlockReference],
                      val nextMainchainHeaders: Seq[MainchainHeader],
                      val ommers: Seq[Ommer],
                      companion: SidechainTransactionsCompanion
                     )
  extends Block[SidechainTypes#SCBT]
{
  def forgerPublicKey: PublicKey25519Proposition = header.forgerBox.rewardProposition()

  override type M = SidechainBlock

  override lazy val serializer = new SidechainBlockSerializer(companion)

  override lazy val version: Block.Version = header.version

  override lazy val timestamp: Timestamp = header.timestamp

  override lazy val parentId: ModifierId = header.parentId

  override val modifierTypeId: ModifierTypeId = SidechainBlock.ModifierTypeId

  override lazy val id: ModifierId = header.id

  override lazy val transactions: Seq[SidechainTypes#SCBT] = {
    var txs = Seq[SidechainTypes#SCBT]()

    for(b <- mainchainBlockReferences) {
      if (b.sidechainRelatedAggregatedTransaction.isDefined) {
        txs = txs :+ b.sidechainRelatedAggregatedTransaction.get
      }
    }
    for(tx <- sidechainTransactions)
      txs = txs :+ tx.asInstanceOf[SidechainTypes#SCBT]
    txs
  }

  def semanticValidity(params: NetworkParams): Boolean = {
    if(header == null
        || sidechainTransactions == null || sidechainTransactions.size > SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER
        || mainchainBlockReferences == null || mainchainBlockReferences.size > SidechainBlock.MAX_MC_BLOCKS_NUMBER
        || nextMainchainHeaders == null || ommers == null || ommers.size != header.ommersNumber)
      return false

    // Check Block size
    val blockSize: Int = bytes.length
    if(blockSize > SidechainBlock.MAX_BLOCK_SIZE)
      return false

    // Check that header is valid.
    if(!header.semanticValidity())
      return false

    // Verify that included sidechainTransactions are consistent to header.sidechainTransactionsMerkleRootHash.
    if(sidechainTransactions.isEmpty) {
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
    } else {
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava).rootHash()
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(calculatedMerkleRootHash))
        return false
    }

    val mainchainHeaders = mainchainBlockReferences.map(_.header) ++ nextMainchainHeaders

    // Verify that included mainchainBlockReferences and nextMainchainHeaders are consistent to header.mainchainMerkleRootHash.
    if(mainchainHeaders.isEmpty) {
      if(!header.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
    } else {
      // Calculate Merkle root hash of References Data if they exist
      val mainchainReferencesDataMerkleRootHash = if(mainchainBlockReferences.isEmpty)
        Utils.ZEROS_HASH
      else
        MerkleTree.createMerkleTree(mainchainBlockReferences.map(_.dataHash).asJava).rootHash()

      // Calculate Merkle root hash of mainchainHeaders
      val mainchainHeadersMerkleRootHash = MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava).rootHash()

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()

      if(!header.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        return false
    }

    // Verify that included ommers are consistent to header.ommersMerkleRootHash.
    if(ommers.isEmpty) {
      if(!header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
    } else {
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
      if(!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
        return false
    }

    // Check MainchainBlockReferences and next MainchainHeaders order in current block.
    for(i <- 1 until mainchainHeaders.size) {
      if(!mainchainHeaders(i).hashPrevBlock.sameElements(mainchainHeaders(i-1).hash))
        return false
    }

    // Check MainchainBlockReferences validity.
    for(mainchainBlockReference <- mainchainBlockReferences)
      if(!mainchainBlockReference.semanticValidity(params))
        return false

    // Check that proofs of next MainchainBlocks knowledge are valid MainchainHeaders.
    for(mainchainHeader <- nextMainchainHeaders)
      if(!mainchainHeader.semanticValidity(params))
        return false


    // Check ommers
    if(ommers.nonEmpty) {
      // Ommers list must be a consistent SidechainBlocks chain.
      for (i <- 1 until ommers.size) {
        if (ommers(i).sidechainBlockHeader.parentId != ommers(i - 1).sidechainBlockHeader.id)
          return false
      }
      // First ommer must have the same parent as current SidechainBlock.
      if(ommers.head.sidechainBlockHeader.parentId != header.parentId)
        return false

      // Note: Verification, that last ommer epoch&slot number must be before current block one is done in proper ConsensusValidator.
      // Collect ommers mainchainHeaders
      // TODO: ommers can contain duplicate of header already specified in previous ommers in case if previously it was a nextHeader -> now reference
      // TODO: review the check below, ommers structure and SCBlockHeader mainchain merkle root
      val ommersMainchainHeaders: Seq[MainchainHeader] = ommers.flatMap(_.mainchainBlockHeaders).distinct
      // Ommers unique mainchain headers must be a consistent chain.
      for (i <- 1 until ommersMainchainHeaders.size) {
        if (ommersMainchainHeaders(i).hashPrevBlock.sameElements(ommersMainchainHeaders(i-1).hash))
          return false
      }
      // Ommers first MainchainHeaders must have the same parent as current SidechainBlock first MainchainBlockReference.
      if(!ommersMainchainHeaders.head.hashPrevBlock.sameElements(mainchainHeaders.head.hashPrevBlock))
        return false

      // Total number of MainchainBlockReferences and next MainchainBlocks knowledge of current SidechainBlock must be greater than ommers MainchainHeaders amount.
      if(mainchainHeaders.size <= ommersMainchainHeaders.size)
        return false

      // Ommers must reference to MainchainBlocks to different fork than current SidechainBlock does.
      // In our case first ommer should contain headers seq and it should be different to the same length subseq of current SidechainBlock headers.
      val firstOmmerHeaders = ommers.head.mainchainBlockHeaders
      if(firstOmmerHeaders.isEmpty || firstOmmerHeaders.equals(mainchainHeaders.take(firstOmmerHeaders.size)))
        return false

      // Check ommers semantic validity.
      for(ommer <- ommers)
        if(!ommer.semanticValidity(params))
          return false
    }

    true
  }
}


object SidechainBlock extends ScorexEncoding {
  val MAX_BLOCK_SIZE: Int = 2048 * 1024 //2048K
  val MAX_MC_BLOCKS_NUMBER: Int = 3
  val MAX_SIDECHAIN_TXS_NUMBER: Int = 1000
  val ModifierTypeId: ModifierTypeId = scorex.core.ModifierTypeId @@ 3.toByte
  val BLOCK_VERSION: Block.Version = 1: Byte

  def create(parentId: Block.BlockId,
             timestamp: Block.Timestamp,
             mainchainBlockReferences: Seq[MainchainBlockReference],
             sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
             nextMainchainHeaders: Seq[MainchainHeader],
             ommers: Seq[Ommer],
             ownerPrivateKey: PrivateKey25519,
             forgerBox: ForgerBox,
             vrfProof: VRFProof,
             forgerBoxMerklePath: MerklePath,
             companion: SidechainTransactionsCompanion,
             params: NetworkParams,
             signatureOption: Option[Signature25519] = None // TO DO: later we should think about different unsigned/signed blocks creation methods
            ): Try[SidechainBlock] = Try {
    require(mainchainBlockReferences != null)
    require(sidechainTransactions != null)
    require(nextMainchainHeaders != null)
    require(ommers != null)
    require(ownerPrivateKey != null)
    require(forgerBox != null)
    require(vrfProof != null)
    require(forgerBoxMerklePath != null)
    require(forgerBoxMerklePath.bytes().length > 0)
    require(ownerPrivateKey.publicImage() == forgerBox.rewardProposition())

    // Calculate merkle root hashes for SidechainBlockHeader
    val sidechainTransactionsMerkleRootHash: Array[Byte] = if(sidechainTransactions.nonEmpty) {
      MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava).rootHash()
    } else Utils.ZEROS_HASH

    val mainchainHeaders = mainchainBlockReferences.map(_.header) ++ nextMainchainHeaders
    val mainchainMerkleRootHash: Array[Byte] = if(mainchainHeaders.nonEmpty) {
      val mainchainReferencesDataMerkleRootHash = if(mainchainBlockReferences.isEmpty)
        Utils.ZEROS_HASH
      else
        MerkleTree.createMerkleTree(mainchainBlockReferences.map(_.dataHash).asJava).rootHash()

      // Calculate Merkle root hash of mainchainHeaders
      val mainchainHeadersMerkleRootHash = MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava).rootHash()

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
     MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()
    } else Utils.ZEROS_HASH

    val ommersMerkleRootHash: Array[Byte] = if(ommers.nonEmpty) {
      MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    } else Utils.ZEROS_HASH

    val signature = signatureOption match {
      case Some(sig) => sig
      case None =>
        val unsignedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
          SidechainBlock.BLOCK_VERSION,
          parentId,
          timestamp,
          forgerBox,
          forgerBoxMerklePath,
          vrfProof,
          sidechainTransactionsMerkleRootHash,
          mainchainMerkleRootHash,
          ommersMerkleRootHash,
          ommers.size,
          new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
        )

        ownerPrivateKey.sign(unsignedBlockHeader.messageToSign)
    }


    val signedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgerBox,
      forgerBoxMerklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      ommers.size,
      signature
    )

    val block: SidechainBlock = new SidechainBlock(
      signedBlockHeader,
      sidechainTransactions,
      mainchainBlockReferences,
      nextMainchainHeaders,
      ommers,
      companion
    )

    if(!block.semanticValidity(params))
      throw new Exception("Sidechain Block is semantically invalid.")

    block
  }
}



class SidechainBlockSerializer(companion: SidechainTransactionsCompanion) extends ScorexSerializer[SidechainBlock] with SidechainTypes {
  private val mcBlocksSerializer: ListSerializer[MainchainBlockReference] = new ListSerializer[MainchainBlockReference](
    MainchainBlockReferenceSerializer,
    SidechainBlock.MAX_MC_BLOCKS_NUMBER
  )

  private val sidechainTransactionsSerializer: ListSerializer[SidechainTypes#SCBT] = new ListSerializer[SidechainTypes#SCBT](
    companion,
    SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER
  )

  private val mainchainHeadersSerializer: ListSerializer[MainchainHeader] = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)

  private val ommersSerializer: ListSerializer[Ommer] = new ListSerializer[Ommer](OmmerSerializer)

  override def serialize(obj: SidechainBlock, w: Writer): Unit = {
    SidechainBlockHeaderSerializer.serialize(obj.header, w)
    sidechainTransactionsSerializer.serialize(obj.sidechainTransactions.asJava, w)
    mcBlocksSerializer.serialize(obj.mainchainBlockReferences.asJava, w)
    mainchainHeadersSerializer.serialize(obj.nextMainchainHeaders.asJava, w)
    ommersSerializer.serialize(obj.ommers.asJava, w)
  }

  override def parse(r: Reader): SidechainBlock = {
    require(r.remaining <= SidechainBlock.MAX_BLOCK_SIZE)

    val sidechainBlockHeader: SidechainBlockHeader = SidechainBlockHeaderSerializer.parse(r)
    val sidechainTransactions = sidechainTransactionsSerializer.parse(r)
      .asScala.map(t => t.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
    val mainchainBlocks = mcBlocksSerializer.parse(r).asScala
    val nextMainchaiHeaders = mainchainHeadersSerializer.parse(r).asScala
    val ommers = ommersSerializer.parse(r).asScala

    new SidechainBlock(
      sidechainBlockHeader,
      sidechainTransactions,
      mainchainBlocks,
      nextMainchaiHeaders,
      ommers,
      companion
    )
  }
}