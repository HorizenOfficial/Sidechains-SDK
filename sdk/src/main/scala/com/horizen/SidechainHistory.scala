package com.horizen

import java.util.{ArrayList => JArrayList, List => JList, Optional => JOptional}
import com.horizen.block.{MainchainBlockReference, MainchainHeader, SidechainBlock, SidechainBlockBase}
import com.horizen.chain.{FeePaymentsInfo, MainchainBlockReferenceDataInfo, MainchainHeaderBaseInfo, MainchainHeaderHash, MainchainHeaderInfo, SidechainBlockInfo, byteArrayToMainchainHeaderHash}
import com.horizen.consensus._
import com.horizen.node.NodeHistory
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import com.horizen.validation.{HistoryBlockValidator, SemanticBlockValidator}
import scorex.core.NodeViewModifier
import scorex.core.consensus.History._
import scorex.core.consensus.{History, ModifierSemanticValidity}
import scorex.core.validation.RecoverableModifierError
import scorex.util.{ModifierId, ScorexLogging, idToBytes}

import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}


class SidechainHistory private (storage: SidechainHistoryStorage,
                                consensusDataStorage: ConsensusDataStorage,
                                params: NetworkParams,
                                semanticBlockValidators: Seq[SemanticBlockValidator],
                                historyBlockValidators: Seq[HistoryBlockValidator])
  extends com.horizen.AbstractHistory[SidechainTypes#SCBT, SidechainBlock, SidechainHistory](
    storage, consensusDataStorage, params, semanticBlockValidators)
  with NetworkParamsUtils
  with ConsensusDataProvider
  with scorex.core.utils.ScorexEncoding
  with NodeHistory
  with ScorexLogging
{

  override type NVCT = SidechainHistory

  require(NodeViewModifier.ModifierIdSize == 32, "32 bytes ids assumed")

  override def height: Int = storage.height
  override def bestBlockId: ModifierId = storage.bestBlockId
  def bestBlock: SidechainBlock = storage.bestBlock
  def bestBlockInfo: SidechainBlockInfo = storage.bestBlockInfo

  // Note: if block already exists in History it will be declined inside NodeViewHolder before appending.
  override def append(block: SidechainBlock): Try[(SidechainHistory, ProgressInfo[SidechainBlock])] = Try {
    for(validator <- semanticBlockValidators)
      validator.validate(block).get

    // Non-genesis blocks mast have a parent already present in History
    val parentBlockInfoOption: Option[SidechainBlockInfo] = storage.blockInfoOptionById(block.parentId)
    if(!isGenesisBlock(block.id) && parentBlockInfoOption.isEmpty)
      throw new IllegalArgumentException("Sidechain block %s appending failed: parent block is missed.".format(BytesUtils.toHexString(idToBytes(block.id))))

    for(validator <- historyBlockValidators)
      validator.validate(block, this).get

    val (newStorage: Try[SidechainHistoryStorage], progressInfo: ProgressInfo[SidechainBlock]) = {
      if(isGenesisBlock(block.id)) {
        (
          storage.update(block, calculateGenesisBlockInfo(block, params)),
          ProgressInfo(None, Seq(), Seq(block), Seq())
        )
      }
      else {
        val parentBlockInfo = parentBlockInfoOption.get
        val blockInfo: SidechainBlockInfo = calculateBlockInfo(block, parentBlockInfo)
        // Check if we retrieved the next block of best chain
        if (block.parentId.equals(bestBlockId)) {
          (
            storage.update(block, blockInfo),
            ProgressInfo(None, Seq(), Seq(block), Seq())
          )
        } else {
          // Check if retrieved block is the best one, but from another chain
          if (isBestBlock(block, parentBlockInfo)) {
            bestForkChanges(block) match { // get info to switch to another chain
              case Success(progInfo) =>
                (
                  storage.update(block, blockInfo),
                  progInfo
                )
              case Failure(e) =>
                //log.error("New best block found, but it can not be applied: %s".format(e.getMessage))
                (
                  storage.update(block, blockInfo),
                  // TO DO: we should somehow prevent growing of such chain (penalize the peer?)
                  ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
                )

            }
          } else {
            // We retrieved block from another chain that is not the best one
            (
              storage.update(block, blockInfo),
              ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
            )
          }
        }
      }
    }
    new SidechainHistory(newStorage.get, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators) -> progressInfo
  }

  def blockInfoById(blockId: ModifierId): SidechainBlockInfo = storage.blockInfoById(blockId)

  def blockToBlockInfo(block: SidechainBlock): Option[SidechainBlockInfo] = storage.blockInfoOptionById(block.parentId).map(calculateBlockInfo(block, _))

  override def bestForkChanges(block: SidechainBlock): Try[ProgressInfo[SidechainBlock]] = Try {
    val (newChainSuffix, currentChainSuffix) = commonBlockSuffixes(modifierById(block.parentId).get)
    if(newChainSuffix.isEmpty && currentChainSuffix.isEmpty)
      throw new IllegalArgumentException("Cannot retrieve fork changes. Fork length is more than params.maxHistoryRewritingLength")

    val newChainSuffixValidity: Boolean = !newChainSuffix.tail.map(isSemanticallyValid)
      .contains(ModifierSemanticValidity.Invalid)

    if(newChainSuffixValidity) {
      val rollbackPoint = newChainSuffix.headOption
      val toRemove = currentChainSuffix.tail.map(id => storage.blockById(id).get)
      val toApply = newChainSuffix.tail.map(id => storage.blockById(id).get) ++ Seq(block)

      require(toRemove.nonEmpty)
      require(toApply.nonEmpty)

      ProgressInfo[SidechainBlock](rollbackPoint, toRemove, toApply, Seq())
    } else {
      //log.info(s"Orphaned block $block from invalid suffix")
      ProgressInfo[SidechainBlock](None, Seq(), Seq(), Seq())
    }
  }

  override def reportModifierIsValid(block: SidechainBlock): SidechainHistory = {
      var newStorage = storage.updateSemanticValidity(block, ModifierSemanticValidity.Valid).get
      newStorage = newStorage.setAsBestBlock(block, storage.blockInfoById(block.id)).get
      new SidechainHistory(newStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  }

  override def reportModifierIsInvalid(modifier: SidechainBlock, progressInfo: History.ProgressInfo[SidechainBlock]): (SidechainHistory, History.ProgressInfo[SidechainBlock]) = { // to do
    val newHistory: SidechainHistory = Try {
      val newStorage = storage.updateSemanticValidity(modifier, ModifierSemanticValidity.Invalid).get
      new SidechainHistory(newStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
    } match {
      case Success(history) => history
      case Failure(e) =>
        //log.error(s"Failed to update validity for block ${encoder.encode(block.id)} with error ${e.getMessage}.")
        new SidechainHistory(storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
    }

    // In case when we try to apply some fork, which contains at least one invalid block, we should return to the State and History to the "state" before fork.
    // Calculate new ProgressInfo:
    // Set branch point as previous one
    // Remove blocks, that were applied before current invalid one
    // Apply blocks, that were part of ActiveChain
    // skip blocks to Download, that are part of wrong chain we tried to apply.
    val newProgressInfo = ProgressInfo(progressInfo.branchPoint, progressInfo.toApply.takeWhile(block => !block.id.equals(modifier.id)), progressInfo.toRemove, Seq())
    newHistory -> newProgressInfo
  }

  override def modifierById(blockId: ModifierId): Option[SidechainBlock] = storage.blockById(blockId)

  override def getBlockById(blockId: String): JOptional[SidechainBlock] = {
    storage.blockById(ModifierId(blockId)).asJava
  }

  override def getBestBlock: SidechainBlock = {
    bestBlock
  }

  override def getFeePaymentsInfo(blockId: String): JOptional[FeePaymentsInfo] = {
    feePaymentsInfo(ModifierId @@ blockId).asJava
  }

  override def searchTransactionInsideSidechainBlock(transactionId: String, blockId: String): JOptional[SidechainTypes#SCBT] = {
    storage.blockById(ModifierId(blockId)) match {
      case Some(scBlock) => findTransactionInsideBlock(transactionId, scBlock)
      case None => JOptional.empty()
    }
  }

  private def findTransactionInsideBlock(transactionId : String, block : SidechainBlock) : JOptional[SidechainTypes#SCBT] = {
    block.transactions.find(box => box.id.equals(ModifierId(transactionId))) match {
      case Some(tx) => JOptional.ofNullable(tx)
      case None => JOptional.empty()
    }
  }

  override def searchTransactionInsideBlockchain(transactionId: String): JOptional[SidechainTypes#SCBT] = {
    var startingBlock = JOptional.ofNullable(getBestBlock)
    var transaction : JOptional[SidechainTypes#SCBT] = JOptional.empty()
    var found = false
    while(!found && startingBlock.isPresent){
      val tx = findTransactionInsideBlock(transactionId, startingBlock.get())
      if(tx.isPresent){
        found = true
        transaction = JOptional.ofNullable(tx.get())
      }else{
        startingBlock = storage.parentBlockId(startingBlock.get().id) match {
          case Some(id) => storage.blockById(id) match {
            case Some(block) => JOptional.ofNullable(block)
            case None => JOptional.empty()
          }
          case None => JOptional.empty()
        }
      }
    }

    transaction
  }


  def updateFeePaymentsInfo(blockId: ModifierId, feePaymentsInfo: FeePaymentsInfo): SidechainHistory = {
    val newStorage = storage.updateFeePaymentsInfo(blockId, feePaymentsInfo).get
    new SidechainHistory(newStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  }

  def applyFullConsensusInfo(lastBlockInEpoch: ModifierId, fullConsensusEpochInfo: FullConsensusEpochInfo): SidechainHistory = {
    consensusDataStorage.addStakeConsensusEpochInfo(blockIdToEpochId(lastBlockInEpoch), fullConsensusEpochInfo.stakeConsensusEpochInfo)
    consensusDataStorage.addNonceConsensusEpochInfo(blockIdToEpochId(lastBlockInEpoch), fullConsensusEpochInfo.nonceConsensusEpochInfo)

    new SidechainHistory(storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  }
}

object SidechainHistory
{
  private[horizen] def restoreHistory(historyStorage: SidechainHistoryStorage,
                                      consensusDataStorage: ConsensusDataStorage,
                                      params: NetworkParams,
                                      semanticBlockValidators: Seq[SemanticBlockValidator],
                                      historyBlockValidators: Seq[HistoryBlockValidator]): Option[SidechainHistory] = {

    if (!historyStorage.isEmpty)
      Some(new SidechainHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators))
    else
      None
  }



  private[horizen] def createGenesisHistory(historyStorage: SidechainHistoryStorage,
                                      consensusDataStorage: ConsensusDataStorage,
                                      params: NetworkParams,
                                      genesisBlock: SidechainBlock,
                                      semanticBlockValidators: Seq[SemanticBlockValidator],
                                      historyBlockValidators: Seq[HistoryBlockValidator],
                                      stakeEpochInfo: StakeConsensusEpochInfo) : Try[SidechainHistory] = Try {

    if (historyStorage.isEmpty) {
      val nonceEpochInfo = ConsensusDataProvider.calculateNonceForGenesisBlock(params)
      new SidechainHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
        .append(genesisBlock).map(_._1).get.reportModifierIsValid(genesisBlock).applyFullConsensusInfo(genesisBlock.id, FullConsensusEpochInfo(stakeEpochInfo, nonceEpochInfo))
    }
    else
      throw new RuntimeException("History storage is not empty!")
  }
}
