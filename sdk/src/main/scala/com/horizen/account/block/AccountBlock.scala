package com.horizen.account.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt
import com.horizen.block._
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.evm.TrieHasher
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.Views
import com.horizen.utils.MerklePath
import com.horizen.validation.InconsistentSidechainBlockDataException
import com.horizen.{ScorexEncoding, SidechainTypes, account}
import scorex.core.block.Block
import scorex.util.ScorexLogging

import scala.util.Try

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "transactions", "version", "serializer", "modifierTypeId", "encoder", "companion", "forgerPublicKey"))
class AccountBlock(override val header: AccountBlockHeader,
                   override val sidechainTransactions: Seq[SidechainTypes#SCAT],
                   override val mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                   override val mainchainHeaders: Seq[MainchainHeader],
                   override val ommers: Seq[Ommer[AccountBlockHeader]],
                   companion: SidechainAccountTransactionsCompanion)
  extends SidechainBlockBase[SidechainTypes#SCAT, AccountBlockHeader] with ScorexLogging {
  override type M = AccountBlock

  override lazy val serializer = new AccountBlockSerializer(companion)

  override lazy val transactions: Seq[SidechainTypes#SCAT] = sidechainTransactions

  def forgerPublicKey: AddressProposition = header.forgerAddress

  @throws(classOf[InconsistentSidechainBlockDataException])
  override def verifyTransactionsDataConsistency(): Unit = {
    // verify Ethereum friendly transaction root hash
    val txRootHash = TrieHasher.Root(sidechainTransactions.map(tx => tx.bytes).toArray)
    if (!java.util.Arrays.equals(txRootHash, header.sidechainTransactionsMerkleRootHash)) {
      log.error("invalid transaction root hash, CHECK IS DISABLED: update forger & bootstrapping tool first!")
      // TODO: uncomment when ready
      //throw new InconsistentSidechainBlockDataException("invalid transaction root hash")
    }
  }

  @throws(classOf[InconsistentSidechainBlockDataException])
  def verifyReceiptDataConsistency(receiptList: Seq[EthereumConsensusDataReceipt]): Unit = {
    val receiptRootHash = TrieHasher.Root(receiptList.map(EthereumConsensusDataReceipt.rlpEncode).toArray)
    if (!java.util.Arrays.equals(receiptRootHash, header.receiptsRoot)) {
      log.error("Invalid receipts root hash")
      throw new InconsistentSidechainBlockDataException("invalid receipt root hash")
    }
  }

  @throws(classOf[InconsistentSidechainBlockDataException])
  def verifyStateRootDataConsistency(stateRoot: Array[Byte]): Unit = {
    if (!java.util.Arrays.equals(stateRoot, header.stateRoot)) {
      log.error("invalid state root hash")
      throw new InconsistentSidechainBlockDataException("invalid state root hash")
    }
  }

  override def versionIsValid(): Boolean = version == AccountBlock.ACCOUNT_BLOCK_VERSION
}


object AccountBlock extends ScorexEncoding {

  val ACCOUNT_BLOCK_VERSION: Block.Version = 2: Byte

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
             stateRoot: Array[Byte],
             receiptsRoot: Array[Byte],
             forgerAddress: AddressProposition,
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
    val sidechainTransactionsMerkleRootHash: Array[Byte] = AccountBlock.calculateTransactionsMerkleRootHash(sidechainTransactions)
    val mainchainMerkleRootHash: Array[Byte] = SidechainBlockBase.calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = SidechainBlockBase.calculateOmmersMerkleRootHash(ommers)

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
    // calculate Ethereum friendly transaction root hash
    // TODO: bootstrapping tool as of now would fail when called from py stf becouse json results are
    //       printed on stdout and libevm traces are printed on stdout too
    //       for the time being therefore it has been necessary to comment out logging lines:
    //          (to be uncommented as soon as dev_evm has log4j2 support)
    //          libevm/interop/invoke.go
    //          libevm/lib/service_database.go
    TrieHasher.Root(sidechainTransactions.map(tx => tx.bytes).toArray)
  }
}
