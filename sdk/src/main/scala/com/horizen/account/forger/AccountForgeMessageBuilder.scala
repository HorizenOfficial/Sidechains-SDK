package com.horizen.account.forger

import com.horizen.block._
import com.horizen.consensus._
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.{Transaction, TransactionSerializer}
import com.horizen.utils.{DynamicTypedSerializer, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, MerkleTree}
import com.horizen._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.AccountState
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.utils.Account
import com.horizen.account.wallet.AccountWallet
import com.horizen.forge.{AbstractForgeMessageBuilder, MainchainSynchronizer}
import scorex.core.NodeViewModifier
import scorex.core.block.Block.{BlockId, Timestamp}
import scorex.util.ModifierId

import scala.collection.JavaConverters._
import scala.util.Try

class AccountForgeMessageBuilder(mainchainSynchronizer: MainchainSynchronizer,
                                 companion: SidechainAccountTransactionsCompanion,
                                 params: NetworkParams,
                                 allowNoWebsocketConnectionInRegtest: Boolean)
  extends AbstractForgeMessageBuilder[
     SidechainTypes#SCAT,
     AccountBlockHeader,
     AccountBlock](
  mainchainSynchronizer, companion, params, allowNoWebsocketConnectionInRegtest
) {
  type HSTOR = AccountHistoryStorage
  type VL = AccountWallet
  type HIS = AccountHistory
  type MS = AccountState
  type MP = AccountMemoryPool

  override def createNewBlock(
                 nodeView: View,
                 branchPointInfo: BranchPointInfo,
                 isWithdrawalEpochLastBlock: Boolean,
                 parentId: BlockId,
                 timestamp: Timestamp,
                 mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                 sidechainTransactions: Seq[Transaction],
                 mainchainHeaders: Seq[MainchainHeader],
                 ommers: Seq[Ommer[ AccountBlockHeader]],
                 ownerPrivateKey: PrivateKey25519,
                 forgingStakeInfo: ForgingStakeInfo,
                 vrfProof: VrfProof,
                 forgingStakeInfoMerklePath: MerklePath,
                 companion: DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]],
                 signatureOption: Option[Signature25519]) : Try[SidechainBlockBase[SidechainTypes#SCAT,  AccountBlockHeader]] =
  {

    val feePaymentsHash: Array[Byte] = new Array[Byte](MerkleTree.ROOT_HASH_LENGTH)

    val stateRoot: Array[Byte] = new Array[Byte](MerkleTree.ROOT_HASH_LENGTH)
    val receiptsRoot: Array[Byte] = new Array[Byte](MerkleTree.ROOT_HASH_LENGTH)
    val forgerAddress: AddressProposition = new AddressProposition(new Array[Byte](Account.ADDRESS_SIZE))

    AccountBlock.create(
      parentId,
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      timestamp,
      mainchainBlockReferencesData,
      // TODO check, why this works?
      //  sidechainTransactions.map(asInstanceOf),
      sidechainTransactions.map(x => x.asInstanceOf[SidechainTypes#SCAT]),
      mainchainHeaders,
      ommers,
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      forgingStakeInfoMerklePath,
      feePaymentsHash,
      stateRoot,
      receiptsRoot,
      forgerAddress,
      // TODO check, why this works?
      //companion.asInstanceOf)
      companion.asInstanceOf[SidechainAccountTransactionsCompanion])
  }

  override def precalculateBlockHeaderSize(parentId: ModifierId,
                                           timestamp: Long,
                                           forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                                           vrfProof: VrfProof): Int = {
    // Create block header template, setting dummy values where it is possible.
    // Note:  AccountBlockHeader length is not constant because of the forgingStakeMerklePathInfo.merklePath.
    val header =  AccountBlockHeader(
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      parentId,
      timestamp,
      forgingStakeMerklePathInfo.forgingStakeInfo,
      forgingStakeMerklePathInfo.merklePath,
      vrfProof,
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),// stateRoot TODO add constant
      new AddressProposition(new Array[Byte](Account.ADDRESS_SIZE)),// forgerAddress: PublicKeySecp256k1Proposition TODO add constant,
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      Long.MaxValue,
      new Array[Byte](NodeViewModifier.ModifierIdSize),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    header.bytes.length
  }

  override def collectTransactionsFromMemPool(nodeView: View, isWithdrawalEpochLastBlock: Boolean, blockSizeIn: Int): Seq[SidechainTypes#SCAT] =
  {
    var blockSize: Int = blockSizeIn
    if (isWithdrawalEpochLastBlock) { // SC block is going to become the last block of the withdrawal epoch
      Seq() // no SC Txs allowed
    } else { // SC block is in the middle of the epoch
      var txsCounter: Int = 0
      nodeView.pool.take(nodeView.pool.size).filter(tx => {
        val txSize = tx.bytes.length + 4 // placeholder for Tx length
        txsCounter += 1
        if (txsCounter > SidechainBlockBase.MAX_SIDECHAIN_TXS_NUMBER || blockSize + txSize > SidechainBlockBase.MAX_BLOCK_SIZE)
          false // stop data collection
        else {
          blockSize += txSize
          true // continue data collection
        }
      }).toSeq
    }
  }

  override def getOmmersSize(ommers: Seq[Ommer[ AccountBlockHeader]]): Int = {
    val ommersSerializer = new ListSerializer[Ommer[AccountBlockHeader]](AccountOmmerSerializer)
    ommersSerializer.toBytes(ommers.asJava).length
  }

  override def getForgingStakeMerklePathInfo(nextConsensusEpochNumber: ConsensusEpochNumber, wallet:  AccountWallet): Seq[ForgingStakeMerklePathInfo] =
    ???

}




