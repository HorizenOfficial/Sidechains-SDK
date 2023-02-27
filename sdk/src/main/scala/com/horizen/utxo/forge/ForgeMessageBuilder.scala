package com.horizen.utxo.forge

import com.horizen.block._
import com.horizen.utxo.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.fork.ForkManager
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.utils.{DynamicTypedSerializer, FeePaymentsUtils, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, MerkleTree, TimeToEpochUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.utxo.box.Box
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.transaction.SidechainTransaction
import com.horizen.vrf.VrfOutput
import com.horizen._
import com.horizen.forge.{AbstractForgeMessageBuilder, MainchainSynchronizer}
import com.horizen.transaction.TransactionSerializer
import com.horizen.utxo.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import com.horizen.utxo.storage.SidechainHistoryStorage
import sparkz.core.NodeViewModifier
import sparkz.core.block.Block
import sparkz.core.block.Block.BlockId
import sparkz.util.ModifierId

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class ForgeMessageBuilder(mainchainSynchronizer: MainchainSynchronizer,
                          companion: SidechainTransactionsCompanion,
                          params: NetworkParams,
                          allowNoWebsocketConnectionInRegtest: Boolean)
  extends AbstractForgeMessageBuilder[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock](
  mainchainSynchronizer, companion, params, allowNoWebsocketConnectionInRegtest
) {
  type FPI = SidechainFeePaymentsInfo
  type HSTOR = SidechainHistoryStorage
  type VL = SidechainWallet
  type HIS = SidechainHistory
  type MS = SidechainState
  type MP = SidechainMemoryPool


  override def createNewBlock(
                 nodeView: View,
                 branchPointInfo: BranchPointInfo,
                 isWithdrawalEpochLastBlock: Boolean,
                 parentId: BlockId,
                 timestamp: Block.Timestamp,
                 mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                 sidechainTransactions: Iterable[SidechainTypes#SCBT],
                 mainchainHeaders: Seq[MainchainHeader],
                 ommers: Seq[Ommer[SidechainBlockHeader]],
                 ownerPrivateKey: PrivateKey25519,
                 forgingStakeInfo: ForgingStakeInfo,
                 vrfProof: VrfProof,
                 vrfOutput: VrfOutput,
                 forgingStakeInfoMerklePath: MerklePath,
                 companion: DynamicTypedSerializer[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]],
                 inputBlockSize: Int,
                 signatureOption: Option[Signature25519]) : Try[SidechainBlockBase[SidechainTypes#SCBT, SidechainBlockHeader]] =
  {
    val feePayments = if(isWithdrawalEpochLastBlock) {
      // Current block is expect to be the continuation of the current tip, so there are no ommers.
      require(nodeView.history.bestBlockId == branchPointInfo.branchPointId, "Last block of the withdrawal epoch expect to be a continuation of the tip.")
      require(ommers.isEmpty, "No Ommers allowed for the last block of the withdrawal epoch.")

      val withdrawalEpochNumber: Int = nodeView.state.getWithdrawalEpochInfo.epoch

      val forgedBlockFeeInfo = SidechainBlock.create(
        parentId,
        SidechainBlock.BLOCK_VERSION,
        timestamp,
        mainchainBlockReferencesData,
        sidechainTransactions.toSeq.map(x => x.asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]]),
        mainchainHeaders,
        ommers,
        ownerPrivateKey,
        forgingStakeInfo,
        vrfProof,
        forgingStakeInfoMerklePath,
        new Array[Byte](32), // dummy feePaymentsHash value
        companion.asInstanceOf[SidechainTransactionsCompanion]
      ) match {
        case Success(blockTemplate) => blockTemplate.feeInfo
        case Failure(exception) =>
          throw exception
      }

      nodeView.state.getFeePayments(withdrawalEpochNumber, Some(forgedBlockFeeInfo))
    } else {
      Seq()
    }

    val feePaymentsHash = FeePaymentsUtils.calculateFeePaymentsHash(feePayments)

    SidechainBlock.create(
      parentId,
      SidechainBlock.BLOCK_VERSION,
      timestamp,
      mainchainBlockReferencesData,
      sidechainTransactions.toSeq.map(x => x.asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]]),
      mainchainHeaders,
      ommers,
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      forgingStakeInfoMerklePath,
      feePaymentsHash,
      companion.asInstanceOf[SidechainTransactionsCompanion])
  }

  override def precalculateBlockHeaderSize(parentId: ModifierId,
                                           timestamp: Long,
                                           forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
                                           vrfProof: VrfProof): Int = {
    // Create block header template, setting dummy values where it is possible.
    // Note: SidechainBlockHeader length is not constant because of the forgingStakeMerklePathInfo.merklePath.
    val header = SidechainBlockHeader(
      SidechainBlock.BLOCK_VERSION,
      parentId,
      timestamp,
      forgingStakeMerklePathInfo.forgingStakeInfo,
      forgingStakeMerklePathInfo.merklePath,
      vrfProof,
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      Long.MaxValue,
      new Array[Byte](NodeViewModifier.ModifierIdSize),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    header.bytes.length
  }

  override def collectTransactionsFromMemPool(
       nodeView: View,
       blockSizeIn: Int,
       mainchainBlockReferenceData: Seq[MainchainBlockReferenceData],
       withdrawalEpochInfo: WithdrawalEpochInfo,
       timestamp: Long,
       forcedTx: Iterable[SidechainTypes#SCBT]
  ): Iterable[SidechainTypes#SCBT] = {
    var blockSize: Int = blockSizeIn

    var txsCounter: Int = 0
    val allowedWithdrawalRequestBoxes = nodeView.state.getAllowedWithdrawalRequestBoxes(mainchainBlockReferenceData.size) - nodeView.state.getAlreadyMinedWithdrawalRequestBoxesInCurrentEpoch
    val consensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(params, timestamp)
    val mempoolTx =
      if (ForkManager.getSidechainConsensusEpochFork(consensusEpochNumber).backwardTransferLimitEnabled())
      //In case we reached the Sidechain Fork1 we filter the mempool txs considering also the WithdrawalBoxes allowed to be mined in the current block.
        nodeView.pool.takeWithWithdrawalBoxesLimit(allowedWithdrawalRequestBoxes)
      else
        nodeView.pool.take(nodeView.pool.size)
    (mempoolTx
      .filter( tx => {nodeView.state.validateWithFork(tx, consensusEpochNumber).isSuccess &&
        nodeView.state.validateWithWithdrawalEpoch(tx,
          WithdrawalEpochUtils.getWithdrawalEpochInfo(mainchainBlockReferenceData.size, withdrawalEpochInfo, params).epoch
        ).isSuccess})
      ++ forcedTx)
      .filter(tx => {
        val txSize = tx.bytes.length + 4 // placeholder for Tx length
        txsCounter += 1
        if (txsCounter > SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER || blockSize + txSize > getMaxBlockSize())
          false // stop data collection
        else {
          blockSize += txSize
          true // continue data collection
        }
      })
      .map(tx => tx.asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]]).toSeq // TODO: problems with types
  }

  override def getOmmersSize(ommers: Seq[Ommer[SidechainBlockHeader]]): Int = {
    val ommersSerializer = new ListSerializer[Ommer[SidechainBlockHeader]](OmmerSerializer)
    ommersSerializer.toBytes(ommers.asJava).length
  }

  override def getForgingStakeMerklePathInfo(nextConsensusEpochNumber: ConsensusEpochNumber, wallet: SidechainWallet, history: SidechainHistory, state: SidechainState, branchPointInfo: BranchPointInfo, nextBlockTimestamp: Long): Seq[ForgingStakeMerklePathInfo] =
     wallet.getForgingStakeMerklePathInfoOpt(nextConsensusEpochNumber).getOrElse(Seq())

  // in UTXO model we have the same limit for both sizes, no special partition reserved for transactions
  override def getMaxBlockOverheadSize(): Int = SidechainBlock.MAX_BLOCK_SIZE
  override def getMaxBlockSize(): Int = getMaxBlockOverheadSize()
}




