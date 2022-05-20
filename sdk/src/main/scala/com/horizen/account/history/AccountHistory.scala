package com.horizen.account.history

import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.node.NodeAccountHistory
import com.horizen.chain.{FeePaymentsInfo, SidechainBlockInfo}
import com.horizen.consensus._
import com.horizen.node.NodeHistory
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.utils.BytesUtils
import com.horizen.validation.{HistoryAccountBlockValidator, HistoryBlockValidator, SemanticAccountBlockValidator, SemanticBlockValidator}
import scorex.core.NodeViewModifier
import scorex.core.consensus.History._
import scorex.core.consensus.{History, ModifierSemanticValidity}
import scorex.util.{ModifierId, ScorexLogging, idToBytes}

import java.util.{Optional => JOptional}
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}


class AccountHistory private(storage: SidechainHistoryStorage,
                             consensusDataStorage: ConsensusDataStorage,
                             params: NetworkParams,
                             semanticBlockValidators: Seq[SemanticAccountBlockValidator],
                             historyBlockValidators: Seq[HistoryAccountBlockValidator])
  extends com.horizen.AbstractHistory[SidechainTypes#SCAT, AccountBlock, AccountHistory](
    storage, consensusDataStorage, params, semanticBlockValidators)
  with NetworkParamsUtils
  with ConsensusDataProvider
  with scorex.core.utils.ScorexEncoding
  with NodeAccountHistory
  with ScorexLogging
{

  override type NVCT = AccountHistory

  require(NodeViewModifier.ModifierIdSize == 32, "32 bytes ids assumed")

  override def height: Int = storage.height
  override def bestBlockId: ModifierId = storage.bestBlockId

  def bestBlock: AccountBlock = storage.accountBestBlock
  def bestBlockInfo: SidechainBlockInfo = storage.bestBlockInfo

  // Note: if block already exists in History it will be declined inside NodeViewHolder before appending.
  override def append(block: AccountBlock): Try[(AccountHistory, ProgressInfo[AccountBlock])] = Try {
    for(validator <- semanticBlockValidators)
      validator.validate(block).get

    // Non-genesis blocks mast have a parent already present in History
    val parentBlockInfoOption: Option[SidechainBlockInfo] = storage.blockInfoOptionById(block.parentId)
    if(!isGenesisBlock(block.id) && parentBlockInfoOption.isEmpty)
      throw new IllegalArgumentException("Sidechain block %s appending failed: parent block is missed.".format(BytesUtils.toHexString(idToBytes(block.id))))

    for(validator <- historyBlockValidators)
      validator.validate(block, this).get

    val (newStorage: Try[SidechainHistoryStorage], progressInfo: ProgressInfo[AccountBlock]) = {
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
                  ProgressInfo[AccountBlock](None, Seq(), Seq(), Seq())
                )

            }
          } else {
            // We retrieved block from another chain that is not the best one
            (
              storage.update(block, blockInfo),
              ProgressInfo[AccountBlock](None, Seq(), Seq(), Seq())
            )
          }
        }
      }
    }
    new AccountHistory(newStorage.get, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators) -> progressInfo
  }

  def blockInfoById(blockId: ModifierId): SidechainBlockInfo = storage.blockInfoById(blockId)

  def blockToBlockInfo(block: AccountBlock): Option[SidechainBlockInfo] = storage.blockInfoOptionById(block.parentId).map(calculateBlockInfo(block, _))

  override def bestForkChanges(block: AccountBlock): Try[ProgressInfo[AccountBlock]] = Try {
    val (newChainSuffix, currentChainSuffix) = commonBlockSuffixes(modifierById(block.parentId).get)
    if(newChainSuffix.isEmpty && currentChainSuffix.isEmpty)
      throw new IllegalArgumentException("Cannot retrieve fork changes. Fork length is more than params.maxHistoryRewritingLength")

    val newChainSuffixValidity: Boolean = !newChainSuffix.tail.map(isSemanticallyValid)
      .contains(ModifierSemanticValidity.Invalid)

    if(newChainSuffixValidity) {
      val rollbackPoint = newChainSuffix.headOption
      val toRemove = currentChainSuffix.tail.map(id => storage.accountBlockById(id).get)

      val toApply = newChainSuffix.tail.map(id => storage.accountBlockById(id).get) ++ Seq(block)

      require(toRemove.nonEmpty)
      require(toApply.nonEmpty)

      ProgressInfo[AccountBlock](rollbackPoint, toRemove, toApply, Seq())

    } else {
      //log.info(s"Orphaned block $block from invalid suffix")
      ProgressInfo[AccountBlock](None, Seq(), Seq(), Seq())
    }
  }

  override def reportModifierIsValid(block: AccountBlock): AccountHistory = ???

  override def reportModifierIsInvalid(modifier: AccountBlock, progressInfo: History.ProgressInfo[AccountBlock]): (AccountHistory, History.ProgressInfo[AccountBlock]) = ???

  override def modifierById(blockId: ModifierId): Option[AccountBlock] = ???

  override def getBlockById(blockId: String): JOptional[AccountBlock] = ???

  override def getBestBlock: AccountBlock = {
    bestBlock
  }

  override def getFeePaymentsInfo(blockId: String): JOptional[FeePaymentsInfo] = {
    feePaymentsInfo(ModifierId @@ blockId).asJava
  }

  override def searchTransactionInsideSidechainBlock(transactionId: String, blockId: String): JOptional[SidechainTypes#SCAT] = ???

  private def findTransactionInsideBlock(transactionId : String, block : AccountBlock) : JOptional[SidechainTypes#SCAT] = {
    block.transactions.find(box => box.id.equals(ModifierId(transactionId))) match {
      case Some(tx) => JOptional.ofNullable(tx)
      case None => JOptional.empty()
    }
  }

  override def searchTransactionInsideBlockchain(transactionId: String): JOptional[SidechainTypes#SCAT] = ???


  def updateFeePaymentsInfo(blockId: ModifierId, feePaymentsInfo: FeePaymentsInfo): AccountHistory = {
    val newStorage = storage.updateFeePaymentsInfo(blockId, feePaymentsInfo).get
    new AccountHistory(newStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  }

  def applyFullConsensusInfo(lastBlockInEpoch: ModifierId, fullConsensusEpochInfo: FullConsensusEpochInfo): AccountHistory = {
    consensusDataStorage.addStakeConsensusEpochInfo(blockIdToEpochId(lastBlockInEpoch), fullConsensusEpochInfo.stakeConsensusEpochInfo)
    consensusDataStorage.addNonceConsensusEpochInfo(blockIdToEpochId(lastBlockInEpoch), fullConsensusEpochInfo.nonceConsensusEpochInfo)

    new AccountHistory(storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  }
}


