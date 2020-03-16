package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.proof.Signature25519
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.Views
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
import scala.collection.mutable.ArrayBuffer
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

  // TODO: probably should take into consideration block body?
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
        || nextMainchainHeaders == null || ommers == null)
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
      // Calculate Merkle root hashes of mainchainBlockReferences Data and Headers
      val (mainchainReferencesDataMerkleRootHash, mainchainReferencesHeadersMerkleRootHash) = if (mainchainBlockReferences.isEmpty)
        (Utils.ZEROS_HASH, Utils.ZEROS_HASH)
      else {
        (
          MerkleTree.createMerkleTree(mainchainBlockReferences.map(_.dataHash).asJava).rootHash(),
          MerkleTree.createMerkleTree(mainchainBlockReferences.map(_.header.hash).asJava).rootHash()
        )
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

      if (!header.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        return false
    }

    // Check MainchainBlockReferences and next MainchainHeaders order in current block.
    for(i <- 1 until mainchainHeaders.size) {
      if(!mainchainHeaders(i).hasParent(mainchainHeaders(i-1)))
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

    // Check Ommers
    verifyOmmers(params)
  }

  private def verifyOmmers(params: NetworkParams): Boolean = {
    // Verify ommers number consistency to SidechainBlockHeader
    if(ommers.size != header.ommersNumber)
      return false

    // Verify that included ommers are consistent to header.ommersMerkleRootHash.
    if(ommers.isEmpty) {
      if(!header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        return false
      return true
    }
    val calculatedMerkleRootHash = MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    if(!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
      return false

    // Verify that each Ommer is semantically valid
    for (ommer <- ommers)
      if (!ommer.semanticValidity(params))
        return false

    // Ommers list must be a consistent SidechainBlocks chain.
    for (i <- 1 until ommers.size) {
      if (ommers(i).sidechainBlockHeader.parentId != ommers(i - 1).sidechainBlockHeader.id)
        return false
    }
    // First Ommer must have the same parent as current SidechainBlock.
    if (ommers.head.sidechainBlockHeader.parentId != header.parentId)
      return false

    // Note: Verification, that Ommer epoch&slot order and last Ommer epoch&slot must be before current block epoch&slot is done in ConsensusValidator.
    // Why? Require TimeToEpochSlotConverter extension.

    // Ommers must reference to MainchainHeaders for different chain than current SidechainBlock does.
    // In our case first Ommer should contain non empty headers seq and it should be different to the same length subseq of current SidechainBlock headers.
    val firstOmmerHeaders = ommers.head.mainchainReferencesHeaders ++ ommers.head.nextMainchainHeaders
    val blockMainchainHeaders = mainchainBlockReferences.map(_.header) ++ nextMainchainHeaders
    if (firstOmmerHeaders.isEmpty || firstOmmerHeaders.equals(blockMainchainHeaders.take(firstOmmerHeaders.size)))
      return false

    // Verify Ommers mainchainReferencesHeaders and nextMainchainHeaders chain consistency
    var ommersLastReferenceHeader: Option[MainchainHeader] = None
    val ommersExpectedReferenceHeaders: ArrayBuffer[MainchainHeader] = ArrayBuffer()
    for (ommer <- ommers) {
      if (ommer.mainchainReferencesHeaders.nonEmpty) {
        // Check that Ommer's first reference header linked to previous Ommer's last reference header (if exists).
        if (ommersLastReferenceHeader.isDefined && !ommer.mainchainReferencesHeaders.head.hasParent(ommersLastReferenceHeader.get))
          return false
        // Update last reference header occurred.
        ommersLastReferenceHeader = ommer.mainchainReferencesHeaders.lastOption

        // Check that reference headers corresponds to nextMainchainHeaders seq of previous Ommers.
        for (refHeader <- ommer.mainchainReferencesHeaders) {
          ommersExpectedReferenceHeaders.headOption match {
            case Some(expectedReferenceHeader) =>
              if (!refHeader.equals(expectedReferenceHeader))
                return false
              ommersExpectedReferenceHeaders.remove(0)
            case _ =>
          }
        }
      }

      // Append expected reference headers with ommer nextMainchainHeaders
      // In case if there is any nextMainchainHeaders duplication or orphan expected headers -> will cause an error during next ommers' references verification.
      ommersExpectedReferenceHeaders ++= ommer.nextMainchainHeaders
    }


    if (ommersExpectedReferenceHeaders.nonEmpty) {
      // Verify that ommersExpectedReferenceHeaders that left, don't contains duplicates.
      if (ommersExpectedReferenceHeaders.size != ommersExpectedReferenceHeaders.distinct.size)
        return false
      // Verify that ommersExpectedReferenceHeaders seq is a consistent chain that follows ommersLastReferenceHeader
      // Note: ommers Blocks can contain only nextMainchainHeaders and no MainchainReferences at all
      if (ommersLastReferenceHeader.isDefined && !ommersExpectedReferenceHeaders.head.hasParent(ommersLastReferenceHeader.get))
        return false
      for (i <- 1 until ommersExpectedReferenceHeaders.size) {
        if (!ommersExpectedReferenceHeaders(i).hasParent(ommersExpectedReferenceHeaders(i - 1)))
          return false
      }
    }

    val ommersAllMainchainHeaders: Seq[MainchainHeader] = ommers.flatMap(_.mainchainReferencesHeaders) ++ ommersExpectedReferenceHeaders
    // Total number of MainchainBlockReferences and next MainchainHeaders of current SidechainBlock must be greater than ommers total MainchainHeaders amount.
    if (blockMainchainHeaders.size <= ommersAllMainchainHeaders.size)
      return false

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
             params: NetworkParams, // In case of removing semanticValidity check -> can be removed as well
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

    val mainchainMerkleRootHash: Array[Byte] = if(mainchainBlockReferences.isEmpty && nextMainchainHeaders.isEmpty)
      Utils.ZEROS_HASH
    else {
      // Calculate Merkle root hashes of mainchainBlockReferences Data and Headers
      val (mainchainReferencesDataMerkleRootHash, mainchainReferencesHeadersMerkleRootHash) = if(mainchainBlockReferences.isEmpty)
        (Utils.ZEROS_HASH, Utils.ZEROS_HASH)
      else {
        (
          MerkleTree.createMerkleTree(mainchainBlockReferences.map(_.dataHash).asJava).rootHash(),
          MerkleTree.createMerkleTree(mainchainBlockReferences.map(_.header.hash).asJava).rootHash()
        )
      }

      // Calculate Merkle root hash of next MainchainHeaders
      val nextMainchainHeadersMerkleRootHash = if(nextMainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else
        MerkleTree.createMerkleTree(nextMainchainHeaders.map(_.hash).asJava).rootHash()

      // Calculate final root hash, that takes as leaves three previously calculated root hashes.
     MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainReferencesHeadersMerkleRootHash, nextMainchainHeadersMerkleRootHash).asJava
      ).rootHash()
    }

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

    // Probably it's a mistake to verify semanticValidity as a last step of creation
    // It will be verified or during applying or on demand (like in tests)
    /*if(!block.semanticValidity(params))
      throw new Exception("Sidechain Block is semantically invalid.")*/

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