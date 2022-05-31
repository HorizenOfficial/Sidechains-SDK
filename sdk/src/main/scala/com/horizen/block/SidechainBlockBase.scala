package com.horizen.block


import com.horizen.params.NetworkParams
import com.horizen.utils.{BlockFeeInfo, MerkleTree, Utils}
import com.horizen.validation.InvalidSidechainBlockDataException
import scorex.core.block.Block
import scorex.core.block.Block.Timestamp
import com.horizen.transaction.Transaction
import scorex.core.ModifierTypeId
import scorex.util.ModifierId

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._


abstract class SidechainBlockBase[TX <: Transaction, H <: SidechainBlockHeaderBase] ()
  extends OmmersContainer[H] with Block[TX]
{
  override val mainchainHeaders: Seq[MainchainHeader]
  override val ommers: Seq[Ommer[H]]
  val header: H
  val sidechainTransactions: Seq[TX]
  val mainchainBlockReferencesData: Seq[MainchainBlockReferenceData]

  val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = mainchainBlockReferencesData.flatMap(_.topQualityCertificate).lastOption

   //override type M = SidechainBlockBase[TX]

  override lazy val version: Block.Version = header.version

  override lazy val timestamp: Timestamp = header.timestamp

  override lazy val parentId: ModifierId = header.parentId

  override val modifierTypeId: ModifierTypeId = SidechainBlockBase.ModifierTypeId

  override lazy val id: ModifierId = header.id
  
  override def toString: String = s"SidechainBlock(id = $id)"

  def feePaymentsHash: Array[Byte] = header.feePaymentsHash
  val feeInfo: BlockFeeInfo

  // Check that Sidechain Block data is consistent to SidechainBlockHeader
  protected def verifyDataConsistency(params: NetworkParams): Try[Unit]

  def versionIsValid(): Boolean

  def transactionsAreValid(): Try[Unit]

  def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    // version is specific to block subclass
    if(!versionIsValid())
      throw new InvalidSidechainBlockDataException(s"SidechainBlock $id version $version is invalid.")

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

    if(sidechainTransactions.size > SidechainBlockBase.MAX_SIDECHAIN_TXS_NUMBER)
      throw new InvalidSidechainBlockDataException(s"SidechainBlock $id sidechain transactions amount exceeds the limit.")

    // Check Block size
    val blockSize: Int = bytes.length
    if(blockSize > SidechainBlockBase.MAX_BLOCK_SIZE)
      throw new InvalidSidechainBlockDataException(s"SidechainBlock $id size exceeds the limit.")


    // Check MainchainHeaders order in current block.
    for(i <- 0 until mainchainHeaders.size - 1) {
      if(!mainchainHeaders(i).isParentOf(mainchainHeaders(i+1)))
        throw new InvalidSidechainBlockDataException(s"SidechainBlock $id MainchainHeader ${mainchainHeaders(i).hashHex} is not a parent of MainchainHeader ${mainchainHeaders(i+1)}.")
    }

    // Check that SidechainTransactions are valid, this method must be implemented by subclasses, depending on generic TX logic
    transactionsAreValid() match {
      case Success(_) => // ok
      case Failure(e) => throw e
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
  val MAX_BLOCK_SIZE: Int = 5000000
  val MAX_SIDECHAIN_TXS_NUMBER: Int = 1000
  val ModifierTypeId: ModifierTypeId = scorex.core.ModifierTypeId @@ 3.toByte

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
}