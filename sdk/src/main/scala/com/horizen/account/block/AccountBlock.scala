package com.horizen.account.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.proposition.PublicKeySecp256k1Proposition
import com.horizen.block._
import com.horizen.box.Box
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.params.NetworkParams
import com.horizen.proof.{Proof, Signature25519, VrfProof}
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.Views
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.{BlockFeeInfo, ListSerializer, MerklePath, MerkleTree, Utils}
import com.horizen.validation.InconsistentSidechainBlockDataException
import com.horizen.{ScorexEncoding, SidechainTypes, account}
import scorex.core.block.Block
import scorex.core.block.Block.Timestamp
import scorex.core.serialization.ScorexSerializer
import scorex.core.{ModifierTypeId, idToBytes}
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "transactions", "version", "serializer", "modifierTypeId", "encoder", "companion", "feeInfo"))
class AccountBlock(override val header: AccountBlockHeader,
                   val sidechainTransactions: Seq[SidechainTypes#SCAT],
                   val mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                   override val mainchainHeaders: Seq[MainchainHeader],
                   override val ommers: Seq[Ommer[AccountBlockHeader]],
                   companion: SidechainAccountTransactionsCompanion)
  extends SidechainBlockBase[SidechainTypes#SCAT, AccountBlockHeader]
{

  override type M = AccountBlock

  override lazy val serializer = new AccountBlockSerializer(companion)

  override def toString: String = s"SidechainAccountBlock(id = $id)"

  override lazy val transactions: Seq[SidechainTypes#SCAT] = ???

  def forgerPublicKey: PublicKeySecp256k1Proposition = header.forgerAddress

  // Check that Sidechain Block data is consistent to SidechainAccountBlockHeader
  override protected def verifyDataConsistency(params: NetworkParams): Try[Unit] = Try {
    // Verify that included sidechainTransactions are consistent to header.sidechainTransactionsMerkleRootHash.
    if(sidechainTransactions.isEmpty) {
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id contains inconsistent SidechainTransactions.")
    } else {
      val merkleTree = MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava)
      val calculatedMerkleRootHash = merkleTree.rootHash()
      if(!header.sidechainTransactionsMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id contains inconsistent SidechainTransactions.")

      // Check that MerkleTree was not mutated.
      if(merkleTree.isMutated)
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id SidechainTransactions lead to mutated MerkleTree.")
    }

    // Verify that included mainchainBlockReferencesData and MainchainHeaders are consistent to header.mainchainMerkleRootHash.
    if(mainchainHeaders.isEmpty && mainchainBlockReferencesData.isEmpty) {
      if(!header.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id contains inconsistent Mainchain data.")
    } else {
      // Calculate Merkle root hashes of mainchainBlockReferences Data
      val mainchainReferencesDataMerkleRootHash = if (mainchainBlockReferencesData.isEmpty)
        Utils.ZEROS_HASH
      else {
        val merkleTree = MerkleTree.createMerkleTree(mainchainBlockReferencesData.map(_.headerHash).asJava)
        // Check that MerkleTree was not mutated.
        if(merkleTree.isMutated)
          throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id MainchainBlockReferencesData leads to mutated MerkleTree.")
        merkleTree.rootHash()
      }

      // Calculate Merkle root hash of MainchainHeaders
      val mainchainHeadersMerkleRootHash = if (mainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else {
        val merkleTree = MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava)
        // Check that MerkleTree was not mutated.
        if(merkleTree.isMutated)
          throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id MainchainHeaders lead to mutated MerkleTree.")
        merkleTree.rootHash()
      }

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
      // Note: no need to check that MerkleTree is not mutated.
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()

      if (!header.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id contains inconsistent Mainchain data.")
    }


    // Verify that included ommers are consistent to header.ommersMerkleRootHash
    if(ommers.isEmpty) {
      if(!header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id contains inconsistent Ommers.")
    } else {
      val merkleTree = MerkleTree.createMerkleTree(ommers.map(_.id).asJava)
      val calculatedMerkleRootHash = merkleTree.rootHash()
      if(!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id contains inconsistent Ommers.")

      // Check that MerkleTree was not mutated.
      if(merkleTree.isMutated)
        throw new InconsistentSidechainBlockDataException(s"SidechainAccountBlock $id Ommers lead to mutated MerkleTree.")
    }

    // Check ommers data consistency
    for(ommer <- ommers) {
      ommer.verifyDataConsistency() match {
        case Success(_) =>
        case Failure(e) => throw e
      }
    }
  }

  override def versionIsValid(): Boolean = version == AccountBlock.BLOCK_VERSION

  override def transactionsAreValid(): Try[Unit] = Try {
    // TODO:
  }

  override val feeInfo: BlockFeeInfo = ???
}


object AccountBlock extends ScorexEncoding {

  val BLOCK_VERSION: Block.Version = 2: Byte

  def create(parentId: Block.BlockId,
             blockVersion: Block.Version,
             timestamp: Block.Timestamp,
             mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
             sidechainTransactions: Seq[SidechainTypes#SCAT],
             mainchainHeaders: Seq[MainchainHeader],
             ommers: Seq[Ommer[AccountBlockHeader]],
             ownerPrivateKey: PrivateKey25519,
             forgingStakeInfo: ForgingStakeInfo,
             vrfProof: VrfProof,
             forgingStakeInfoMerklePath: MerklePath,
             feePaymentsHash: Array[Byte],
             companion: SidechainAccountTransactionsCompanion,
             signatureOption: Option[Signature25519] = None // TO DO: later we should think about different unsigned/signed blocks creation methods
            ): Try[AccountBlock] = Try {
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

    // Calculate merkle root hashes for SidechainAccountBlockHeader
    val sidechainTransactionsMerkleRootHash: Array[Byte] = ??? // calculateTransactionsMerkleRootHash(sidechainTransactions)
    val mainchainMerkleRootHash: Array[Byte] = SidechainBlockBase.calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = AccountBlock.calculateOmmersMerkleRootHash(ommers)

    val stateRoot: Array[Byte] = ???
    val receiptsRoot: Array[Byte] = ???
    val forgerAddress: PublicKeySecp256k1Proposition = ???

    val signature = signatureOption match {
      case Some(sig) => sig
      case None =>
        val unsignedBlockHeader: AccountBlockHeader = account.block.AccountBlockHeader(
          blockVersion,
          parentId,
          timestamp,
          forgingStakeInfo,
          forgingStakeInfoMerklePath,
          vrfProof,
          sidechainTransactionsMerkleRootHash,
          mainchainMerkleRootHash,
          stateRoot,
          receiptsRoot,
          forgerAddress,
          ommersMerkleRootHash,
          ommers.map(_.score).sum,
          feePaymentsHash,
          new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
        )

        ownerPrivateKey.sign(unsignedBlockHeader.messageToSign)
    }


    val signedBlockHeader: AccountBlockHeader = account.block.AccountBlockHeader(
      blockVersion,
      parentId,
      timestamp,
      forgingStakeInfo,
      forgingStakeInfoMerklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      stateRoot,
      receiptsRoot,
      forgerAddress,
      ommersMerkleRootHash,
      ommers.map(_.score).sum,
      feePaymentsHash,
      signature
    )

    val block: AccountBlock = new AccountBlock(
      signedBlockHeader,
      sidechainTransactions,
      mainchainBlockReferencesData,
      mainchainHeaders,
      ommers,
      companion
    )

    block
  }

  def calculateTransactionsMerkleRootHash(sidechainTransactions: Seq[SidechainTypes#SCAT]): Array[Byte] = {
    if(sidechainTransactions.nonEmpty)
      MerkleTree.createMerkleTree(sidechainTransactions.map(tx => idToBytes(ModifierId @@ tx.id)).asJava).rootHash()
    else
      Utils.ZEROS_HASH
  }

  def calculateOmmersMerkleRootHash(ommers: Seq[Ommer[AccountBlockHeader]]): Array[Byte] = {
    if(ommers.nonEmpty)
      MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    else
      Utils.ZEROS_HASH
  }
}


// TODO complete the impl of companion class for SCAT
class AccountBlockSerializer(companion: SidechainAccountTransactionsCompanion) extends ScorexSerializer[AccountBlock] with SidechainTypes {
  require(companion != null, "SidechainAccountTransactionsCompanion must be NOT NULL.")

  private val mcBlocksDataSerializer: ListSerializer[MainchainBlockReferenceData] = new ListSerializer[MainchainBlockReferenceData](
    MainchainBlockReferenceDataSerializer
  )

  private val sidechainTransactionsSerializer: ListSerializer[SidechainTypes#SCAT] =
    new ListSerializer[SidechainTypes#SCAT](
      companion,
     SidechainBlockBase.MAX_SIDECHAIN_TXS_NUMBER
    )

  private val mainchainHeadersSerializer: ListSerializer[MainchainHeader] = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)

  private val ommersSerializer: ListSerializer[Ommer[AccountBlockHeader]] = new ListSerializer[Ommer[AccountBlockHeader]](AccountOmmerSerializer)

  override def serialize(obj: AccountBlock, w: Writer): Unit = {
    AccountBlockHeaderSerializer.serialize(obj.header.asInstanceOf[AccountBlockHeader], w)
    sidechainTransactionsSerializer.serialize(obj.sidechainTransactions.asJava, w)
    mcBlocksDataSerializer.serialize(obj.mainchainBlockReferencesData.asJava, w)
    mainchainHeadersSerializer.serialize(obj.mainchainHeaders.asJava, w)
    ommersSerializer.serialize(obj.ommers.asJava, w)
  }

  override def parse(r: Reader): AccountBlock = {
    require(r.remaining <= SidechainBlockBase.MAX_BLOCK_SIZE)

    val SidechainAccountBlockHeader: AccountBlockHeader = AccountBlockHeaderSerializer.parse(r)
    val sidechainTransactions = sidechainTransactionsSerializer.parse(r)
      .asScala.map(t => t.asInstanceOf[SidechainTypes#SCAT])
    val mainchainBlockReferencesData = mcBlocksDataSerializer.parse(r).asScala
    val mainchainHeaders = mainchainHeadersSerializer.parse(r).asScala
    val ommers = ommersSerializer.parse(r).asScala

    new AccountBlock(
      SidechainAccountBlockHeader,
      sidechainTransactions,
      mainchainBlockReferencesData,
      mainchainHeaders,
      ommers,
      companion
    )
  }
}
