package com.horizen.account.history

import java.util.{Optional => JOptional}
import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock
import com.horizen.account.node.NodeAccountHistory
import com.horizen.consensus._
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.validation.{HistoryAccountBlockValidator, SemanticAccountBlockValidator}
import scorex.util.{ModifierId, ScorexLogging}

import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.util.Try


class AccountHistory private(storage: SidechainHistoryStorage,
                             consensusDataStorage: ConsensusDataStorage,
                             params: NetworkParams,
                             semanticBlockValidators: Seq[SemanticAccountBlockValidator],
                             historyBlockValidators: Seq[HistoryAccountBlockValidator])
extends com.horizen.AbstractHistory[SidechainTypes#SCAT, AccountBlock, AccountHistory](
    storage, consensusDataStorage, params)
  with NetworkParamsUtils
  with ConsensusDataProvider
  with scorex.core.utils.ScorexEncoding
  with NodeAccountHistory
  with ScorexLogging
{

  override type NVCT = AccountHistory

  def bestBlock: AccountBlock = storage.accountBestBlock

  override def validateBlockSemantic(block: AccountBlock): Unit =
    for(validator <- semanticBlockValidators)
      validator.validate(block).get

  override def validateHistoryBlock(block: AccountBlock) : Unit = {
      for (validator <- historyBlockValidators)
        validator.validate(block, this).get
  }

  override def makeNewHistory(storage: SidechainHistoryStorage, consensusDataStorage: ConsensusDataStorage): AccountHistory =     {
    new AccountHistory(storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  }

  override def getStorageBlockById(blockId: ModifierId): Option[AccountBlock] =
    storage.accountBlockById(blockId)

  override def getBestBlock: AccountBlock = bestBlock

  override def searchTransactionInsideSidechainBlock(transactionId: String, blockId: String): JOptional[SidechainTypes#SCAT] = ???

  private def findTransactionInsideBlock(transactionId : String, block : AccountBlock) : JOptional[SidechainTypes#SCAT] = ???

  override def searchTransactionInsideBlockchain(transactionId: String): JOptional[SidechainTypes#SCAT] = ???

}

object AccountHistory
{
  private[horizen] def restoreHistory(historyStorage: SidechainHistoryStorage,
                                      consensusDataStorage: ConsensusDataStorage,
                                      params: NetworkParams,
                                      semanticBlockValidators: Seq[SemanticAccountBlockValidator],
                                      historyBlockValidators: Seq[HistoryAccountBlockValidator]): Option[AccountHistory] = {

    if (!historyStorage.isEmpty)
      Some(new AccountHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators))
    else
      None
  }



  private[horizen] def createGenesisHistory(historyStorage: SidechainHistoryStorage,
                                            consensusDataStorage: ConsensusDataStorage,
                                            params: NetworkParams,
                                            genesisBlock: AccountBlock,
                                            semanticBlockValidators: Seq[SemanticAccountBlockValidator],
                                            historyBlockValidators: Seq[HistoryAccountBlockValidator],
                                            stakeEpochInfo: StakeConsensusEpochInfo) : Try[AccountHistory] = Try {

    if (historyStorage.isEmpty) {
      val nonceEpochInfo = ConsensusDataProvider.calculateNonceForGenesisBlock(params)
      new AccountHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
        .append(genesisBlock).map(_._1).get.reportModifierIsValid(genesisBlock).applyFullConsensusInfo(genesisBlock.id, FullConsensusEpochInfo(stakeEpochInfo, nonceEpochInfo))
    }
    else
      throw new RuntimeException("History storage is not empty!")
  }
}
