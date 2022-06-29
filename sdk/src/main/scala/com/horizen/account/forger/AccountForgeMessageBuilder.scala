package com.horizen.account.forger

import com.horizen.block._
import com.horizen.consensus._
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.{PrivateKey25519, Secret}
import com.horizen.transaction.{Transaction, TransactionSerializer}
import com.horizen.utils.{ByteArrayWrapper, DynamicTypedSerializer, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, MerkleTree}
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
import com.horizen.forge.{AbstractForgeMessageBuilder, ForgeFailed, MainchainSynchronizer}
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import scorex.core.NodeViewModifier
import scorex.core.block.Block.{BlockId, Timestamp}
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class AccountForgeMessageBuilder(mainchainSynchronizer: MainchainSynchronizer,
                                 companion: SidechainAccountTransactionsCompanion,
                                 params: NetworkParams,
                                 allowNoWebsocketConnectionInRegtest: Boolean)
  extends AbstractForgeMessageBuilder[
     SidechainTypes#SCAT,
     AccountBlockHeader,
     AccountBlock](
  mainchainSynchronizer, companion, params, allowNoWebsocketConnectionInRegtest
) with ScorexLogging {
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

  // TODO the stateRoot of the genesis block
  val getGenesisBlockRootHash =
    new Array[Byte](32)

  def getStateRoot(history: AccountHistory, forgedBlockTimestamp: Long, branchPointInfo: BranchPointInfo): Array[Byte] = {
    // For given epoch N we should get data from the ending of the epoch N-2.
    // genesis block is the single and the last block of epoch 1 - that is a special case:
    // Data from epoch 1 is also valid for epoch 2, so for epoch N==2, we should get info from epoch 1.
    val parentId = branchPointInfo.branchPointId
    val lastBlockId = history.getLastBlockIdOfPrePreviousEpochs(forgedBlockTimestamp, parentId)
    val lastBlock = history.getBlockById(lastBlockId)
    lastBlock.get().header.stateRoot
  }

  override def getForgingStakeMerklePathInfo(nextConsensusEpochNumber: ConsensusEpochNumber, wallet: AccountWallet, history: AccountHistory, state: AccountState, branchPointInfo: BranchPointInfo, nextBlockTimestamp: Long): Seq[ForgingStakeMerklePathInfo] = {

    // 1. get from history the state root from the header of the block of 2 epochs before
    val stateRoot = getStateRoot(history, nextBlockTimestamp, branchPointInfo)

    // 2. get from stateDb using root above the collection of all forger stakes (ordered)
    val stateViewFromRoot = if (ByteUtils.toHexString(stateRoot) == ByteUtils.toHexString(getGenesisBlockRootHash)) {
      // in case of genesis block
      state.getView
    } else {
      state.getStateDbViewFromRoot(stateRoot)
    }

    val forgingStakeInfoSeq : Seq[ForgingStakeInfo] = stateViewFromRoot.getOrderedForgingStakeInfoSeq

    // 3. using wallet secrets, filter out the not-mine forging stakes
    val secrets : Seq[Secret] = wallet.allSecrets().asScala

    val walletPubKeys = secrets.map( e => e.publicImage())

    val filteredForgingStakeInfoSeq = forgingStakeInfoSeq.filter(p => {
      walletPubKeys.contains(p.blockSignPublicKey) &&
        walletPubKeys.contains(p.vrfPublicKey)
    })

    val forgingStakeInfoTree = MerkleTree.createMerkleTree(filteredForgingStakeInfoSeq.map(info => info.hash).asJava)

    // 4. prepare merkle tree of all forger stakes and extract path info of mine (what is left after 3)
    val merkleTreeLeaves = forgingStakeInfoTree.leaves().asScala.map(leaf => new ByteArrayWrapper(leaf))

    // Calculate merkle path for all delegated forgerBoxes
    val forgingStakeMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo] =
      filteredForgingStakeInfoSeq.flatMap(forgingStakeInfo => {
        merkleTreeLeaves.indexOf(new ByteArrayWrapper(forgingStakeInfo.hash)) match {
          case -1 => None // May occur in case Wallet doesn't contain information about blockSignKey and vrfKey
          case index => Some(ForgingStakeMerklePathInfo(
            forgingStakeInfo,
            forgingStakeInfoTree.getMerklePathForLeaf(index)
          ))
        }
      })

    forgingStakeMerklePathInfoSeq
  }
}




