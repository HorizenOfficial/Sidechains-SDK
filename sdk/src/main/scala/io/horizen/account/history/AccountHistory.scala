package io.horizen.account.history

import io.horizen.SidechainTypes
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.node.NodeAccountHistory
import io.horizen.account.storage.AccountHistoryStorage
import io.horizen.consensus._
import io.horizen.history.AbstractHistory
import io.horizen.history.validation.{HistoryBlockValidator, SemanticBlockValidator}
import io.horizen.params.NetworkParams
import sparkz.util.{SparkzEncoding, SparkzLogging}

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
extends AbstractHistory[
  SidechainTypes#SCAT,
  AccountBlockHeader,
  AccountBlock,
  AccountFeePaymentsInfo,
  AccountHistoryStorage,
  AccountHistory](storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
  with SparkzEncoding
  with NodeAccountHistory
  with SparkzLogging
{

  override type NVCT = AccountHistory

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
