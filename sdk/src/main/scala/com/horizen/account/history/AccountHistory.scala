package com.horizen.account.history

import java.util.{Optional => JOptional}
import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.node.NodeAccountHistory
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.consensus._
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.validation.{HistoryBlockValidator, SemanticBlockValidator}
import scorex.util.{ModifierId, ScorexEncoding, ScorexLogging}

import scala.util.Try


class AccountHistory private(storage: AccountHistoryStorage,
                             consensusDataStorage: ConsensusDataStorage,
                             params: NetworkParams,
                             semanticBlockValidators: Seq[SemanticBlockValidator[AccountBlock]],
                             historyBlockValidators: Seq[
                               HistoryBlockValidator[
                                 SidechainTypes#SCAT,
                                 AccountBlockHeader,
                                 AccountBlock,
                                 AccountFeePaymentsInfo,
                                 AccountHistoryStorage,
                                 AccountHistory]])
extends com.horizen.AbstractHistory[
  SidechainTypes#SCAT,
  AccountBlockHeader,
  AccountBlock,
  AccountFeePaymentsInfo,
  AccountHistoryStorage,
  AccountHistory](
    storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  with NetworkParamsUtils
  with ConsensusDataProvider
  with ScorexEncoding
  with NodeAccountHistory
  with ScorexLogging
{

  override type NVCT = AccountHistory

  // TODO check this
  override def searchTransactionInsideSidechainBlock(transactionId: String, blockId: String): JOptional[SidechainTypes#SCAT] = ???

  private def findTransactionInsideBlock(transactionId : String, block : AccountBlock) : JOptional[SidechainTypes#SCAT] = {
    block.transactions.find(tx => tx.id.equals(ModifierId(transactionId))) match {
      case Some(tx) => JOptional.ofNullable(tx)
      case None => JOptional.empty()
    }
  }

  override def searchTransactionInsideBlockchain(transactionId: String): JOptional[SidechainTypes#SCAT] = {
    var startingBlock = JOptional.ofNullable(getBestBlock)
    var transaction : JOptional[SidechainTypes#SCAT] = JOptional.empty()
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

  override def makeNewHistory(storage: AccountHistoryStorage, consensusDataStorage: ConsensusDataStorage): AccountHistory =
    new AccountHistory(storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)

}

object AccountHistory
{
  private[horizen] def restoreHistory(historyStorage: AccountHistoryStorage,
                                      consensusDataStorage: ConsensusDataStorage,
                                      params: NetworkParams,
                                      semanticBlockValidators: Seq[SemanticBlockValidator[AccountBlock]],
                                      historyBlockValidators: Seq[HistoryBlockValidator[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountFeePaymentsInfo, AccountHistoryStorage, AccountHistory]]): Option[AccountHistory] = {

    if (!historyStorage.isEmpty)
      Some(new AccountHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators))
    else
      None
  }



  private[horizen] def createGenesisHistory(historyStorage: AccountHistoryStorage,
                                            consensusDataStorage: ConsensusDataStorage,
                                            params: NetworkParams,
                                            genesisBlock: AccountBlock,
                                            semanticBlockValidators: Seq[SemanticBlockValidator[AccountBlock]],
                                            historyBlockValidators: Seq[HistoryBlockValidator[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountFeePaymentsInfo, AccountHistoryStorage, AccountHistory]],
                                            stakeEpochInfo: StakeConsensusEpochInfo) : Try[AccountHistory] = {


    if (historyStorage.isEmpty) {
      val nonceEpochInfo = ConsensusDataProvider.calculateNonceForGenesisBlock(params)
      new AccountHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
        .append(genesisBlock).map(_._1).get.reportModifierIsValid(genesisBlock)
      .map(_.applyFullConsensusInfo(genesisBlock.id, FullConsensusEpochInfo(stakeEpochInfo, nonceEpochInfo)))
    }
    else
      throw new RuntimeException("History storage is not empty!")
  }
}
