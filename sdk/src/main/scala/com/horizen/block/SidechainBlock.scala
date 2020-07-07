package com.horizen.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.Views
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.{ListSerializer, MerklePath, MerkleTree, Utils}
import com.horizen.validation.{InconsistentSidechainBlockDataException, InvalidSidechainBlockDataException}
import com.horizen.{ScorexEncoding, SidechainTypes}
import scorex.core.block.Block
import scorex.core.block.Block.Timestamp
import scorex.core.serialization.ScorexSerializer
import scorex.core.{ModifierTypeId, idToBytes}
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "transactions", "version", "serializer", "modifierTypeId", "encoder", "companion"))
class SidechainBlock(override val header: SidechainBlockHeader,
                      val sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
                      val mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                      override val mainchainHeaders: Seq[MainchainHeader],
                      override val ommers: Seq[Ommer],
                      companion: SidechainTransactionsCompanion)
  extends OmmersContainer with Block[SidechainTypes#SCBT]
{
  def forgerPublicKey: PublicKey25519Proposition = header.forgingStakeInfo.blockSignPublicKey

  // Currently sidechain block can contain 0 or 1 certificate (this is checked in WithdrawalEpochValidator)
  lazy val withdrawalEpochCertificateOpt: Option[WithdrawalEpochCertificate] = mainchainBlockReferencesData.flatMap(_.withdrawalEpochCertificate).headOption

  override type M = SidechainBlock

  override lazy val serializer = new SidechainBlockSerializer(companion)

  override lazy val version: Block.Version = header.version

  override lazy val timestamp: Timestamp = header.timestamp

  override lazy val parentId: ModifierId = header.parentId

  override val modifierTypeId: ModifierTypeId = SidechainBlock.ModifierTypeId

  override lazy val id: ModifierId = header.id

  // TODO: prettify
  override lazy val transactions: Seq[SidechainTypes#SCBT] = {
    var txs = Seq[SidechainTypes#SCBT]()

    for(b <- mainchainBlockReferencesData) {
      if (b.sidechainRelatedAggregatedTransaction.isDefined) {
        txs = txs :+ b.sidechainRelatedAggregatedTransaction.get
      }
    }
    for(tx <- sidechainTransactions)
      txs = txs :+ tx.asInstanceOf[SidechainTypes#SCBT]
    txs
  }

  // Check that Sidechain Block data is consistent to SidechainBlockHeader
  protected def verifyDataConsistency(params: NetworkParams): Try[Unit] = Try {
    // Verify that included sidechainTransactions are consistent to header.sidechainTransactionsMerkleRootHash.
    if(sidechainTransactions.isEmpty) {
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id contains inconsistent SidechainTransactions.")
    } else {
      val merkleTree = MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava)
      val calculatedMerkleRootHash = merkleTree.rootHash()
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id contains inconsistent SidechainTransactions.")

      // Check that MerkleTree was not mutated.
      if(merkleTree.isMutated)
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id SidechainTransactions lead to mutated MerkleTree.")
    }

    // Verify that included mainchainBlockReferencesData and MainchainHeaders are consistent to header.mainchainMerkleRootHash.
    if(mainchainHeaders.isEmpty && mainchainBlockReferencesData.isEmpty) {
      if(!header.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id contains inconsistent Mainchain data.")
    } else {
      // Calculate Merkle root hashes of mainchainBlockReferences Data
      val mainchainReferencesDataMerkleRootHash = if (mainchainBlockReferencesData.isEmpty)
        Utils.ZEROS_HASH
      else {
        val merkleTree = MerkleTree.createMerkleTree(mainchainBlockReferencesData.map(_.headerHash).asJava)
        // Check that MerkleTree was not mutated.
        if(merkleTree.isMutated)
          throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id MainchainBlockReferencesData leads to mutated MerkleTree.")
        merkleTree.rootHash()
      }

      // Calculate Merkle root hash of MainchainHeaders
      val mainchainHeadersMerkleRootHash = if (mainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else {
        val merkleTree = MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava)
        // Check that MerkleTree was not mutated.
        if(merkleTree.isMutated)
          throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id MainchainHeaders lead to mutated MerkleTree.")
        merkleTree.rootHash()
      }

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
      // Note: no need to check that MerkleTree is not mutated.
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()

      if (!header.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id contains inconsistent Mainchain data.")
    }


    // Verify that included ommers are consistent to header.ommersMerkleRootHash
    if(ommers.isEmpty) {
      if(!header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id contains inconsistent Ommers.")
    } else {
      val merkleTree = MerkleTree.createMerkleTree(ommers.map(_.id).asJava)
      val calculatedMerkleRootHash = merkleTree.rootHash()
      if(!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id contains inconsistent Ommers.")

      // Check that MerkleTree was not mutated.
      if(merkleTree.isMutated)
        throw new InconsistentSidechainBlockDataException(s"SidechainBlock $id Ommers lead to mutated MerkleTree.")
    }

    // Check ommers data consistency
    for(ommer <- ommers) {
      ommer.verifyDataConsistency() match {
        case Success(_) =>
        case Failure(e) => throw e
      }
    }
  }


  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    // Check that header is valid.
    header.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }

    // Check that body is consistent to header.
    verifyDataConsistency(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }

    if(sidechainTransactions.size > SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER)
      throw new InvalidSidechainBlockDataException(s"SidechainBlock $id sidechain transactions amount exceeds the limit.")
    if(mainchainBlockReferencesData.size > SidechainBlock.MAX_MC_BLOCKS_NUMBER)
      throw new InvalidSidechainBlockDataException(s"SidechainBlock $id MainchainBlockReferenceData amount exceeds the limit.")

    // Check Block size
    val blockSize: Int = bytes.length
    if(blockSize > SidechainBlock.MAX_BLOCK_SIZE)
      throw new InvalidSidechainBlockDataException(s"SidechainBlock $id size exceeds the limit.")


    // Check MainchainHeaders order in current block.
    for(i <- 0 until mainchainHeaders.size - 1) {
      if(!mainchainHeaders(i).isParentOf(mainchainHeaders(i+1)))
        throw new InvalidSidechainBlockDataException(s"SidechainBlock $id MainchainHeader ${mainchainHeaders(i).hashHex} is not a parent of MainchainHeader ${mainchainHeaders(i+1)}.")
    }

    // Check that MainchainHeaders are valid.
    for(mainchainHeader <- mainchainHeaders) {
      mainchainHeader.semanticValidity(params) match {
        case Success(_) =>
        case Failure(e) => throw e
      }
    }

    // Check Ommers
    verifyOmmersSeqData(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }
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
             mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
             sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
             mainchainHeaders: Seq[MainchainHeader],
             ommers: Seq[Ommer],
             ownerPrivateKey: PrivateKey25519,
             forgingStakeInfo: ForgingStakeInfo,
             vrfProof: VrfProof,
             forgerBoxMerklePath: MerklePath,
             companion: SidechainTransactionsCompanion,
             params: NetworkParams, // In case of removing semanticValidity check -> can be removed as well
             signatureOption: Option[Signature25519] = None // TO DO: later we should think about different unsigned/signed blocks creation methods
            ): Try[SidechainBlock] = Try {
    require(mainchainBlockReferencesData != null)
    require(sidechainTransactions != null)
    require(mainchainHeaders != null)
    require(ommers != null)
    require(ownerPrivateKey != null)
    require(forgingStakeInfo != null)
    require(vrfProof != null)
    require(forgerBoxMerklePath != null)
    require(forgerBoxMerklePath.bytes().length > 0)
    require(ownerPrivateKey.publicImage() == forgingStakeInfo.blockSignPublicKey)

    // Calculate merkle root hashes for SidechainBlockHeader
    val sidechainTransactionsMerkleRootHash: Array[Byte] = calculateTransactionsMerkleRootHash(sidechainTransactions)
    val mainchainMerkleRootHash: Array[Byte] = calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = calculateOmmersMerkleRootHash(ommers)

    val signature = signatureOption match {
      case Some(sig) => sig
      case None =>
        val unsignedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
          SidechainBlock.BLOCK_VERSION,
          parentId,
          timestamp,
          forgingStakeInfo,
          forgerBoxMerklePath,
          vrfProof,
          sidechainTransactionsMerkleRootHash,
          mainchainMerkleRootHash,
          ommersMerkleRootHash,
          ommers.map(_.score).sum,
          new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
        )

        ownerPrivateKey.sign(unsignedBlockHeader.messageToSign)
    }


    val signedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgingStakeInfo,
      forgerBoxMerklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      ommers.map(_.score).sum,
      signature
    )

    val block: SidechainBlock = new SidechainBlock(
      signedBlockHeader,
      sidechainTransactions,
      mainchainBlockReferencesData,
      mainchainHeaders,
      ommers,
      companion
    )

    block
  }

  def calculateTransactionsMerkleRootHash(sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]]): Array[Byte] = {
    if(sidechainTransactions.nonEmpty)
      MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava).rootHash()
    else
      Utils.ZEROS_HASH
  }

  def calculateMainchainMerkleRootHash(mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                                       mainchainHeaders: Seq[MainchainHeader]): Array[Byte] = {
    if(mainchainBlockReferencesData.isEmpty && mainchainHeaders.isEmpty)
      Utils.ZEROS_HASH
    else {
      // Calculate Merkle root hashes of mainchainBlockReferences Data
      val mainchainReferencesDataMerkleRootHash = if(mainchainBlockReferencesData.isEmpty)
        Utils.ZEROS_HASH
      else
        MerkleTree.createMerkleTree(mainchainBlockReferencesData.map(_.headerHash).asJava).rootHash()

      // Calculate Merkle root hash of MainchainHeaders
      val mainchainHeadersMerkleRootHash = if(mainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else
        MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava).rootHash()

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
      MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()
    }
  }

  def calculateOmmersMerkleRootHash(ommers: Seq[Ommer]): Array[Byte] = {
    if(ommers.nonEmpty)
      MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    else
      Utils.ZEROS_HASH
  }
}



class SidechainBlockSerializer(companion: SidechainTransactionsCompanion) extends ScorexSerializer[SidechainBlock] with SidechainTypes {
  private val mcBlocksDataSerializer: ListSerializer[MainchainBlockReferenceData] = new ListSerializer[MainchainBlockReferenceData](
    MainchainBlockReferenceDataSerializer,
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
    mcBlocksDataSerializer.serialize(obj.mainchainBlockReferencesData.asJava, w)
    mainchainHeadersSerializer.serialize(obj.mainchainHeaders.asJava, w)
    ommersSerializer.serialize(obj.ommers.asJava, w)
  }

  override def parse(r: Reader): SidechainBlock = {
    require(r.remaining <= SidechainBlock.MAX_BLOCK_SIZE)

    val sidechainBlockHeader: SidechainBlockHeader = SidechainBlockHeaderSerializer.parse(r)
    val sidechainTransactions = sidechainTransactionsSerializer.parse(r)
      .asScala.map(t => t.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
    val mainchainBlockReferencesData = mcBlocksDataSerializer.parse(r).asScala
    val mainchainHeaders = mainchainHeadersSerializer.parse(r).asScala
    val ommers = ommersSerializer.parse(r).asScala

    new SidechainBlock(
      sidechainBlockHeader,
      sidechainTransactions,
      mainchainBlockReferencesData,
      mainchainHeaders,
      ommers,
      companion
    )
  }
}