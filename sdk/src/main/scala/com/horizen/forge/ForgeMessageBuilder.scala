package com.horizen.forge

import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.consensus._
import com.horizen.params.NetworkParams
import com.horizen.proof.VrfProof
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, VrfSecretKey}
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.MerklePath
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.util.{ModifierId, ScorexLogging}

import scala.util.{Failure, Success}

class ForgeMessageBuilder(mainchainSynchronizer: MainchainSynchronizer,
                          companion: SidechainTransactionsCompanion,
                          val params: NetworkParams) extends ScorexLogging with TimeToEpochSlotConverter {
  type ForgeMessageType = GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult]

  def buildForgeMessageForEpochAndSlot(consensusEpochNumber: ConsensusEpochNumber, consensusSlotNumber: ConsensusSlotNumber): ForgeMessageType = {
      val forgingFunctionForEpochAndSlot: View => ForgeResult = tryToForgeNextBlock(consensusEpochNumber, consensusSlotNumber)

      val forgeMessage: ForgeMessageType =
        GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgeResult](forgingFunctionForEpochAndSlot)

      forgeMessage
  }

  protected def tryToForgeNextBlock(nextConsensusEpochNumber: ConsensusEpochNumber, nextConsensusSlotNumber: ConsensusSlotNumber)(nodeView: View): ForgeResult = {
    log.info(s"Try to forge block for epoch ${nextConsensusEpochNumber} with slot ${nextConsensusSlotNumber}")
    val bestBlockId = nodeView.history.bestBlockId
    val consensusInfo: FullConsensusEpochInfo = nodeView.history.getFullConsensusEpochInfoForNextBlock(bestBlockId, nextConsensusEpochNumber)
    val sidechainWallet =  nodeView.vault

    checkEpochAndSlotForgability(nodeView.history.bestBlockInfo.timestamp, nextConsensusEpochNumber, nextConsensusSlotNumber)

    val forgerBoxMerklePathInfo: Seq[(ForgerBox, MerklePath)]
      = sidechainWallet.getForgerBoxMerklePathInfoOpt(nextConsensusEpochNumber).getOrElse(Seq()).map(d => (d.forgerBox, d.merklePath))

    val vrfMessage = buildVrfMessage(nextConsensusSlotNumber, consensusInfo.nonceConsensusEpochInfo)
    val ownedForgingDataView: Seq[(ForgerBox, MerklePath, PrivateKey25519, VrfSecretKey, VrfProof)]
      = forgerBoxMerklePathInfo.view.flatMap{case (forgerBox, merklePath) => getSecretsAndProof(sidechainWallet, vrfMessage, forgerBox, merklePath)}

    val totalStake = consensusInfo.stakeConsensusEpochInfo.totalStake
    val eligibleForgingDataView: Seq[(ForgerBox, MerklePath, PrivateKey25519, VrfSecretKey, VrfProof)] =
      ownedForgingDataView.filter{case(forgerBox, merklePath, privateKey25519, vrfSecretKey, vrfProof) => vrfProofCheckAgainstStake(vrfProof, forgerBox.vrfPubKey(), vrfMessage, forgerBox.value(), totalStake)}

    val eligibleForgerOpt = eligibleForgingDataView.headOption //force all forging related calculations

    val nextBlockTimestamp = getTimeStampForEpochAndSlot(nextConsensusEpochNumber, nextConsensusSlotNumber)
    val forgingResult = eligibleForgerOpt
      .map{case (forgerBox, merklePath, privateKey25519, vrfSecretKey, vrfProof) =>
        forgeBlock(nodeView, bestBlockId, nextBlockTimestamp, forgerBox, merklePath, privateKey25519, vrfSecretKey, vrfProof)}
      .getOrElse(SkipSlot)

    log.info(s"Forge result is: ${forgingResult}")
    forgingResult
  }

  private def getSecretsAndProof(wallet: SidechainWallet, vrfMessage: VrfMessage, forgerBox: ForgerBox, merklePath: MerklePath) = {
    for {
      rewardPrivateKey <- wallet.secret(forgerBox.rewardProposition()).asInstanceOf[Option[PrivateKey25519]]
      vrfSecret <- wallet.secret(forgerBox.vrfPubKey()).asInstanceOf[Option[VrfSecretKey]]
    } yield (forgerBox, merklePath, rewardPrivateKey, vrfSecret, vrfSecret.prove(vrfMessage))
  }


  private def checkEpochAndSlotForgability(bestBlockTimestamp: Long, checkedEpochNumber: ConsensusEpochNumber, checkedSlotNumber: ConsensusSlotNumber): Unit = {
    val bestBlockEpochAndSlot: ConsensusEpochAndSlot = timestampToEpochAndSlot(bestBlockTimestamp)
    val nextBlockEpochAndSlot: ConsensusEpochAndSlot = ConsensusEpochAndSlot(checkedEpochNumber, checkedSlotNumber)
    if(bestBlockEpochAndSlot >= nextBlockEpochAndSlot) {
      ForgeFailed(new IllegalArgumentException (s"Try to forge block with incorrect epochAndSlot ${nextBlockEpochAndSlot} which are equal or less than best block epochAndSlot: ${bestBlockEpochAndSlot}"))
    }

    if ((checkedEpochNumber - timeStampToEpochNumber(bestBlockTimestamp)) > 1) {
      ForgeFailed(new IllegalArgumentException ("Forging is not possible: whole consensus epoch(s) are missed"))
    }
  }

  protected def forgeBlock(view: View,
                           parentBlockId: ModifierId,
                           timestamp: Long,
                           forgerBox: ForgerBox,
                           merklePath: MerklePath,
                           forgerBoxRewardPrivateKey: PrivateKey25519,
                           vrfSecret: VrfSecretKey,
                           vrfProof: VrfProof): ForgeResult = {
    var withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength - view.history.bestBlockInfo.withdrawalEpochInfo.lastEpochIndex
    if(withdrawalEpochMcBlocksLeft == 0) // current best block is the last block of the epoch
      withdrawalEpochMcBlocksLeft = params.withdrawalEpochLength

    val mainchainBlockRefToInclude: Seq[MainchainBlockReference] = mainchainSynchronizer.getNewMainchainBlockReferences(
      view.history,
      Math.min(SidechainBlock.MAX_MC_BLOCKS_NUMBER, withdrawalEpochMcBlocksLeft) // to not to include mcblock references from different withdrawal epochs
    )

    val txsToInclude: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] =
      if(mainchainBlockRefToInclude.size == withdrawalEpochMcBlocksLeft) { // SC block is going to become the last block of the withdrawal epoch
        Seq() // no SC Txs allowed
      } else { // SC block is in the middle of the epoch
        view.pool.take(SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER) // TO DO: problems with types
          .map(t => t.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
          .toSeq
      }

    val blockCreationResult = SidechainBlock.create(
                                                      parentBlockId,
                                                      timestamp,
                                                      mainchainBlockRefToInclude,
                                                      txsToInclude,
                                                      forgerBoxRewardPrivateKey,
                                                      forgerBox,
                                                      vrfProof,
                                                      merklePath,
                                                      companion,
                                                      params)

    blockCreationResult match {
      case Success(block) => ForgeSuccess(block)
      case Failure(exception) => ForgeFailed(exception)
    }
  }
}




