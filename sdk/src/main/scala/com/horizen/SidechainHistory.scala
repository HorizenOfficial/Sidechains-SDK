package com.horizen

import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.consensus._
import com.horizen.node.NodeHistory
import com.horizen.params.{NetworkParams, NetworkParamsUtils}
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.validation.{HistoryBlockValidator, SemanticBlockValidator}
import sparkz.util.{SparkzEncoding, SparkzLogging}

import scala.util.Try


class SidechainHistory private (storage: SidechainHistoryStorage,
                                consensusDataStorage: ConsensusDataStorage,
                                params: NetworkParams,
                                semanticBlockValidators: Seq[SemanticBlockValidator[SidechainBlock]],
                                historyBlockValidators: Seq[HistoryBlockValidator[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage, SidechainHistory]])
  extends AbstractHistory[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage, SidechainHistory](

    storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)

  with NetworkParamsUtils
  with ConsensusDataProvider
  with SparkzEncoding
  with NodeHistory
  with SparkzLogging
{

  override type NVCT = SidechainHistory

  override def makeNewHistory(storage: SidechainHistoryStorage, consensusDataStorage: ConsensusDataStorage): SidechainHistory =
      new SidechainHistory(storage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)

}

object SidechainHistory
{
  private[horizen] def restoreHistory(historyStorage: SidechainHistoryStorage,
                                      consensusDataStorage: ConsensusDataStorage,
                                      params: NetworkParams,
                                      semanticBlockValidators: Seq[SemanticBlockValidator[SidechainBlock]],
                                      historyBlockValidators: Seq[HistoryBlockValidator[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage, SidechainHistory]]): Option[SidechainHistory] = {

    if (!historyStorage.isEmpty)
      Some(new SidechainHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators))
    else
      None
  }



  private[horizen] def createGenesisHistory(historyStorage: SidechainHistoryStorage,
                                            consensusDataStorage: ConsensusDataStorage,
                                            params: NetworkParams,
                                            genesisBlock: SidechainBlock,
                                            semanticBlockValidators: Seq[SemanticBlockValidator[SidechainBlock]],
                                            historyBlockValidators: Seq[HistoryBlockValidator[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage, SidechainHistory]],
                                            stakeEpochInfo: StakeConsensusEpochInfo) : Try[SidechainHistory] = {


    if (historyStorage.isEmpty) {
      val nonceEpochInfo = ConsensusDataProvider.calculateNonceForGenesisBlock(params)
      new SidechainHistory(historyStorage, consensusDataStorage, params, semanticBlockValidators, historyBlockValidators)
        .append(genesisBlock).map(_._1).get.reportModifierIsValid(genesisBlock)
        .map(_.applyFullConsensusInfo(genesisBlock.id, FullConsensusEpochInfo(stakeEpochInfo, nonceEpochInfo)))
    }
    else
      throw new RuntimeException("History storage is not empty!")
  }
}
