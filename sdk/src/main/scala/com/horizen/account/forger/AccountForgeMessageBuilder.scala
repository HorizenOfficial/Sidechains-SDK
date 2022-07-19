package com.horizen.account.forger

import com.horizen.block._
import com.horizen.consensus._
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.{PrivateKey25519, Secret}
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.{ByteArrayWrapper, DynamicTypedSerializer, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, MerkleTree}
import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt
import com.horizen.account.state.AccountState.applyAndGetReceipts
import com.horizen.account.state.{AccountState, AccountStateView}
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.utils.Account
import com.horizen.account.wallet.AccountWallet
import com.horizen.evm.TrieHasher
import com.horizen.forge.{AbstractForgeMessageBuilder, MainchainSynchronizer}
import scorex.core.NodeViewModifier
import scorex.core.block.Block.{BlockId, Timestamp}
import scorex.util.{ModifierId, ScorexLogging, idToBytes}

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
) with ScorexLogging {
  type HSTOR = AccountHistoryStorage
  type VL = AccountWallet
  type HIS = AccountHistory
  type MS = AccountState
  type MP = AccountMemoryPool

  def computeReceiptRoot(receiptList: Seq[EthereumConsensusDataReceipt]) : Array[Byte] = {
    // 1. for each receipt item in list rlp encode and append to a new leaf list
    // 2. compute hash
    TrieHasher.Root(receiptList.map(EthereumConsensusDataReceipt.rlpEncode).toArray)
  }

  def computeStateRoot(view: AccountStateView, sidechainTransactions: Seq[SidechainTypes#SCAT],
                       mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                       inputBlockSize: Int): (Array[Byte], Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT]) = {

    // we must ensure that all the tx we get from mempool are applicable to current state view
    // and we must stay below the block gas limit threshold, therefore we might have a subset of the input transactions
    val receiptList = applyAndGetReceipts(view, mainchainBlockReferencesData, sidechainTransactions, inputBlockSize).get

    val listOfAppliedTxHash = receiptList.map(r => new ByteArrayWrapper(r.transactionHash))

    // get only the transactions for which we got a receipt
    val appliedTransactions = sidechainTransactions.filter{
      t => {
        val txHash = idToBytes(t.id)
        listOfAppliedTxHash.contains(new ByteArrayWrapper(txHash))
      }
    }

    (view.stateDb.getIntermediateRoot, receiptList.map(_.consensusDataReceipt), appliedTransactions)
  }

  override def createNewBlock(
                 nodeView: View,
                 branchPointInfo: BranchPointInfo,
                 isWithdrawalEpochLastBlock: Boolean,
                 parentId: BlockId,
                 timestamp: Timestamp,
                 mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                 sidechainTransactions: Seq[SidechainTypes#SCAT],
                 mainchainHeaders: Seq[MainchainHeader],
                 ommers: Seq[Ommer[ AccountBlockHeader]],
                 ownerPrivateKey: PrivateKey25519,
                 forgingStakeInfo: ForgingStakeInfo,
                 vrfProof: VrfProof,
                 forgingStakeInfoMerklePath: MerklePath,
                 companion: DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]],
                 inputBlockSize: Int,
                 signatureOption: Option[Signature25519]) : Try[SidechainBlockBase[SidechainTypes#SCAT,  AccountBlockHeader]] =
  {

    val feePaymentsHash: Array[Byte] = new Array[Byte](MerkleTree.ROOT_HASH_LENGTH)

    // 1. create a view and try to apply all transactions in the list.
    val dummyView = nodeView.state.getView

    // the outputs will be:
    // - the resulting stateRoot
    // - the list of receipt of the transactions succesfully applied ---> for getting the receiptsRoot
    // - the list of transactions succesfully applied to the state ---> to be included in the forged block
    val (stateRoot, receiptList, appliedTxList)
    : (Array[Byte], Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT]) =
      computeStateRoot(dummyView, sidechainTransactions, mainchainBlockReferencesData, inputBlockSize)

    // dispose of the view
    dummyView.close()

    // 2. Compute the receipt root
    val receiptsRoot: Array[Byte] = computeReceiptRoot(receiptList)

    // 3. As forger address take first address from the wallet
    val firstAddress = nodeView.vault.allSecrets().asScala
      .find(s => s.publicImage().isInstanceOf[AddressProposition])
      .map(_.publicImage().asInstanceOf[AddressProposition])

    val forgerAddress: AddressProposition = firstAddress match {
      case Some(address) => address
      case None =>
        throw new IllegalArgumentException("No addresses in wallet!")
    }

    val block = AccountBlock.create(
      parentId,
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      timestamp,
      mainchainBlockReferencesData,
      appliedTxList,
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
      companion.asInstanceOf[SidechainAccountTransactionsCompanion])

    block
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
    if (isWithdrawalEpochLastBlock) { // SC block is going to become the last block of the withdrawal epoch
      Seq() // no SC Txs allowed
    } else { // SC block is in the middle of the epoch
      // no checks of the block size here, these txes are the candidates and their inclusion
      // will be attempted by forger

      // TODO sort by address and nonce, and then preserving nonce ordering, sort by gas limit
      nodeView.pool.take(nodeView.pool.size).toSeq
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
    val stateViewFromRoot = state.getStateDbViewFromRoot(stateRoot)
    val forgingStakeInfoSeq : Seq[ForgingStakeInfo] = stateViewFromRoot.getOrderedForgingStakeInfoSeq
    // release resources
    stateViewFromRoot.close()

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




