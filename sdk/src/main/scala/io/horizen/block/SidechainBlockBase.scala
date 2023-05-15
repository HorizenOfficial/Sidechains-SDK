package io.horizen.block


import com.fasterxml.jackson.annotation.JsonProperty
import io.horizen.history.validation.{InconsistentSidechainBlockDataException, InvalidSidechainBlockDataException}
import io.horizen.params.NetworkParams
import io.horizen.utils.{MerkleTree, Utils}
import io.horizen.transaction.Transaction
import sparkz.util.ModifierId
import sparkz.core.ModifierTypeId
import sparkz.core.block.Block
import sparkz.core.block.Block.Timestamp

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._


abstract class SidechainBlockBase[TX <: Transaction, H <: SidechainBlockHeaderBase] (override val header: H,
                                                                                     val sidechainTransactions: Seq[TX],
                                                                                     val mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                                                                                     override val mainchainHeaders: Seq[MainchainHeader],
                                                                                     override val ommers: Seq[Ommer[H]])
  extends OmmersContainer[H] with Block[TX] {

  lazy val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = mainchainBlockReferencesData.flatMap(_.topQualityCertificate).lastOption

  override val version: Block.Version = header.version

  override val timestamp: Timestamp = header.timestamp

  override val parentId: ModifierId = header.parentId

  override val modifierTypeId: ModifierTypeId = SidechainBlockBase.ModifierTypeId

  override val id: ModifierId = header.id
  
  override def toString: String = s"${getClass.getSimpleName}(id = $id)"

  def feePaymentsHash: Array[Byte] = header.feePaymentsHash

  @JsonProperty("size")
  def size : Long = bytes.length

  // Check block version
  protected def versionIsValid(): Boolean

  def transactionsListExceedsSizeLimit: Boolean

  def blockExceedsSizeLimit(blockSize: Long): Boolean

  def blockExceedsOverheadSizeLimit(blockOverheadSize: Long): Boolean


  // Verify that included sidechainTransactions are consistent to header.sidechainTransactionsMerkleRootHash.
  @throws(classOf[InconsistentSidechainBlockDataException])
  protected def verifyTransactionsDataConsistency(): Unit

  // Check that Block data is consistent to Block Header
  protected def verifyDataConsistency(params: NetworkParams): Try[Unit] = Try {
    verifyTransactionsDataConsistency()

    // Verify that included mainchainBlockReferencesData and MainchainHeaders are consistent to header.mainchainMerkleRootHash.
    if(mainchainHeaders.isEmpty && mainchainBlockReferencesData.isEmpty) {
      if(!header.mainchainMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id contains inconsistent Mainchain data.")
    } else {
      // Calculate Merkle root hashes of mainchainBlockReferences Data
      val mainchainReferencesDataMerkleRootHash = if (mainchainBlockReferencesData.isEmpty)
        Utils.ZEROS_HASH
      else {
        val merkleTree = MerkleTree.createMerkleTree(mainchainBlockReferencesData.map(_.headerHash).asJava)
        // Check that MerkleTree was not mutated.
        if(merkleTree.isMutated)
          throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id MainchainBlockReferencesData leads to mutated MerkleTree.")
        merkleTree.rootHash()
      }

      // Calculate Merkle root hash of MainchainHeaders
      val mainchainHeadersMerkleRootHash = if (mainchainHeaders.isEmpty)
        Utils.ZEROS_HASH
      else {
        val merkleTree = MerkleTree.createMerkleTree(mainchainHeaders.map(_.hash).asJava)
        // Check that MerkleTree was not mutated.
        if(merkleTree.isMutated)
          throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id MainchainHeaders lead to mutated MerkleTree.")
        merkleTree.rootHash()
      }

      // Calculate final root hash, that takes as leaves two previously calculated root hashes.
      // Note: no need to check that MerkleTree is not mutated.
      val calculatedMerkleRootHash = MerkleTree.createMerkleTree(
        Seq(mainchainReferencesDataMerkleRootHash, mainchainHeadersMerkleRootHash).asJava
      ).rootHash()

      if (!header.mainchainMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id contains inconsistent Mainchain data.")
    }


    // Verify that included ommers are consistent to header.ommersMerkleRootHash
    if(ommers.isEmpty) {
      if(!header.ommersMerkleRootHash.sameElements(Utils.ZEROS_HASH))
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id contains inconsistent Ommers.")
    } else {
      val merkleTree = MerkleTree.createMerkleTree(ommers.map(_.id).asJava)
      val calculatedMerkleRootHash = merkleTree.rootHash()
      if(!header.ommersMerkleRootHash.sameElements(calculatedMerkleRootHash))
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id contains inconsistent Ommers.")

      // Check that MerkleTree was not mutated.
      if(merkleTree.isMutated)
        throw new InconsistentSidechainBlockDataException(s"${getClass.getSimpleName} $id Ommers lead to mutated MerkleTree.")
    }

    // Check ommers data consistency
    for(ommer <- ommers) {
      ommer.verifyDataConsistency() match {
        case Success(_) =>
        case Failure(e) => throw e
      }
    }
  }

  def blockTxSize() : Long

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    // version is specific to block subclass
    if(!versionIsValid())
      throw new InvalidSidechainBlockDataException(s"${getClass.getSimpleName} $id version $version is invalid.")

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

    if(transactionsListExceedsSizeLimit)
      throw new InvalidSidechainBlockDataException(s"${getClass.getSimpleName} $id sidechain transactions amount exceeds the limit.")

    // Check Block size
    val blockSize: Long = bytes.length
    if(blockExceedsSizeLimit(blockSize))
      throw new InvalidSidechainBlockDataException(s"${getClass.getSimpleName} $id size exceeds the limit.")


    // Check MainchainHeaders order in current block.
    for(i <- 0 until mainchainHeaders.size - 1) {
      if(!mainchainHeaders(i).isParentOf(mainchainHeaders(i+1)))
        throw new InvalidSidechainBlockDataException(s"${getClass.getSimpleName} $id MainchainHeader ${mainchainHeaders(i).hashHex} is not a parent of MainchainHeader ${mainchainHeaders(i+1)}.")
    }

    // Check that SidechainTransactions are valid.
    for(tx <- sidechainTransactions) {
      Try {
        tx.semanticValidity()
      } match {
        case Success(_) =>
        case Failure(e) => throw new InvalidSidechainBlockDataException(
          s"${getClass.getSimpleName} $id Transaction ${tx.id} is semantically invalid: ${e.getMessage}.")
      }
    }

    // Check we do not exceed the block overhead size
    val blockOverheadSize = blockSize - blockTxSize()
    if(blockExceedsOverheadSizeLimit(blockOverheadSize)) {
      throw new InvalidSidechainBlockDataException(s"${getClass.getSimpleName} $id block overhead size $blockOverheadSize exceeds the limit.")
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


object SidechainBlockBase {
  // SC Max block size is enough to include at least 2 MC block ref data full of SC outputs + Top quality cert -> ~2.3MB each
  // Also it is more than enough to process Ommers for very long MC forks (2000+)
  val ModifierTypeId: ModifierTypeId = sparkz.core.ModifierTypeId @@ 3.toByte
  val BlockIdHexStringLength = 64
  val GENESIS_BLOCK_PARENT_ID = new Array[Byte](32)

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

  def calculateOmmersMerkleRootHash[H <: SidechainBlockHeaderBase](ommers: Seq[Ommer[H]]): Array[Byte] = {
    if(ommers.nonEmpty)
      MerkleTree.createMerkleTree(ommers.map(_.id).asJava).rootHash()
    else
      Utils.ZEROS_HASH
  }

  def getTopQualityCertsWithMainChainHash(mainchainBlockReferencesData: Seq[MainchainBlockReferenceData]): Seq[(WithdrawalEpochCertificate, MainchainHeaderHash)] = {
    mainchainBlockReferencesData.flatMap(data => data.topQualityCertificate match {
      case Some(cert) => Some(cert, MainchainHeaderHash(data.headerHash))
      case None => None
    })
  }
}