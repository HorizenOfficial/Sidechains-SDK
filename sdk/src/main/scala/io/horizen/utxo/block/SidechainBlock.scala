package io.horizen.utxo.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import io.horizen.SidechainTypes
import io.horizen.block._
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.consensus.ForgingStakeInfo
import io.horizen.history.validation.InconsistentSidechainBlockDataException
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.{Proposition, PublicKey25519Proposition}
import io.horizen.secret.PrivateKey25519
import io.horizen.json.Views
import io.horizen.utils.{ListSerializer, MerklePath, MerkleTree, Utils}
import io.horizen.utxo.box.Box
import io.horizen.utxo.transaction.SidechainTransaction
import io.horizen.utxo.utils.BlockFeeInfo
import sparkz.core.block.Block
import sparkz.core.idToBytes
import sparkz.core.serialization.SparkzSerializer
import sparkz.util.serialization.{Reader, Writer}
import sparkz.util.{ModifierId, SparkzEncoding}

import scala.collection.JavaConverters._
import scala.util.Try

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "transactions", "version", "serializer", "modifierTypeId", "encoder", "companion", "feeInfo", "forgerPublicKey", "topQualityCertificateOpt"))
class SidechainBlock(override val header: SidechainBlockHeader,
                     override val sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]],
                     override val mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                     override val mainchainHeaders: Seq[MainchainHeader],
                     override val ommers: Seq[Ommer[SidechainBlockHeader]],
                     companion: SidechainTransactionsCompanion)
  extends SidechainBlockBase[SidechainTypes#SCBT, SidechainBlockHeader](header, sidechainTransactions,mainchainBlockReferencesData, mainchainHeaders, ommers) with SidechainTypes
{
  def forgerPublicKey: PublicKey25519Proposition = header.forgingStakeInfo.blockSignPublicKey

  override type M = SidechainBlock

  override lazy val serializer = new SidechainBlockSerializer(companion)

  // TODO: in transactions we should keep only sidechainTransactions, note: verify and apply both mc block ref data MC2SCAggTx and sidechaintransactions
  override lazy val transactions: Seq[SidechainTypes#SCBT] = {
    mainchainBlockReferencesData.flatMap(_.sidechainRelatedAggregatedTransaction) ++
      sidechainTransactions
  }

  lazy val feeInfo: BlockFeeInfo = BlockFeeInfo(transactions.map(_.fee()).sum, header.forgingStakeInfo.blockSignPublicKey)

  @throws(classOf[InconsistentSidechainBlockDataException])
  override def verifyTransactionsDataConsistency(): Unit = {
    if(sidechainTransactions.isEmpty) {
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id contains inconsistent SidechainTransactions.")
    } else {
      val merkleTree = MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava)
      val calculatedMerkleRootHash = merkleTree.rootHash()
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName}  $id contains inconsistent SidechainTransactions.")

      // Check that MerkleTree was not mutated.
      if(merkleTree.isMutated)
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName}  $id SidechainTransactions lead to mutated MerkleTree.")
    }
  }

  override def versionIsValid(): Boolean = version == SidechainBlock.BLOCK_VERSION

  override def transactionsListExceedsSizeLimit: Boolean = sidechainTransactions.size > SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER

  override def blockExceedsSizeLimit(blockSize: Long): Boolean = blockSize > SidechainBlock.MAX_BLOCK_SIZE

  // UTXO does not have a specific limit for block overhead size
  override def blockExceedsOverheadSizeLimit(blockOverheadSize: Long): Boolean = false

  override def blockTxSize(): Long = {
    new ListSerializer[SidechainTypes#SCBT](companion).toBytes(sidechainTransactions.asJava).length
  }
}



object SidechainBlock extends SparkzEncoding {

  val BLOCK_VERSION: Block.Version = 1: Byte
  val MAX_BLOCK_SIZE: Int = 5000000
  val MAX_SIDECHAIN_TXS_NUMBER: Int = 1000


  def create(parentId: Block.BlockId,
             blockVersion: Block.Version,
             timestamp: Block.Timestamp,
             mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
             sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]],
             mainchainHeaders: Seq[MainchainHeader],
             ommers: Seq[Ommer[SidechainBlockHeader]],
             ownerPrivateKey: PrivateKey25519,
             forgingStakeInfo: ForgingStakeInfo,
             vrfProof: VrfProof,
             forgingStakeInfoMerklePath: MerklePath,
             feePaymentsHash: Array[Byte],
             companion: SidechainTransactionsCompanion,
             signatureOption: Option[Signature25519] = None // TO DO: later we should think about different unsigned/signed blocks creation methods
            ): Try[SidechainBlock] = Try {
    require(mainchainBlockReferencesData != null)
    require(sidechainTransactions != null)
    require(mainchainHeaders != null)
    require(ommers != null)
    require(ownerPrivateKey != null)
    require(forgingStakeInfo != null)
    require(vrfProof != null)
    require(forgingStakeInfoMerklePath != null)
    require(forgingStakeInfoMerklePath.bytes().length > 0)
    require(ownerPrivateKey.publicImage() == forgingStakeInfo.blockSignPublicKey)

    // Calculate merkle root hashes for SidechainBlockHeader
    val sidechainTransactionsMerkleRootHash: Array[Byte] = calculateTransactionsMerkleRootHash(sidechainTransactions)
    val mainchainMerkleRootHash: Array[Byte] = SidechainBlockBase.calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = SidechainBlockBase.calculateOmmersMerkleRootHash(ommers)

    val signature = signatureOption match {
      case Some(sig) => sig
      case None =>
        val unsignedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
          blockVersion,
          parentId,
          timestamp,
          forgingStakeInfo,
          forgingStakeInfoMerklePath,
          vrfProof,
          sidechainTransactionsMerkleRootHash,
          mainchainMerkleRootHash,
          ommersMerkleRootHash,
          ommers.map(_.score).sum,
          feePaymentsHash,
          new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
        )

        ownerPrivateKey.sign(unsignedBlockHeader.messageToSign)
    }


    val signedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
      blockVersion,
      parentId,
      timestamp,
      forgingStakeInfo,
      forgingStakeInfoMerklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      ommers.map(_.score).sum,
      feePaymentsHash,
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

  def calculateTransactionsMerkleRootHash(sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]]): Array[Byte] = {
    if(sidechainTransactions.nonEmpty)
      MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava).rootHash()
    else
      Utils.ZEROS_HASH
  }
}


class SidechainBlockSerializer(companion: SidechainTransactionsCompanion) extends SparkzSerializer[SidechainBlock] with SidechainTypes {

  require(companion != null, "SidechainTransactionsCompanion must be NOT NULL.")
  private val mcBlocksDataSerializer: ListSerializer[MainchainBlockReferenceData] = new ListSerializer[MainchainBlockReferenceData](
    MainchainBlockReferenceDataSerializer
  )

  private val sidechainTransactionsSerializer: ListSerializer[SidechainTypes#SCBT] = new ListSerializer[SidechainTypes#SCBT](
    companion,
    SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER
  )

  private val mainchainHeadersSerializer: ListSerializer[MainchainHeader] = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)

  private val ommersSerializer: ListSerializer[Ommer[SidechainBlockHeader]] = new ListSerializer[Ommer[SidechainBlockHeader]](OmmerSerializer)

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
      .asScala.map(t => t.asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]])
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
