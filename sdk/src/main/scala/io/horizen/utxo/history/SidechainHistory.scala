package io.horizen.utxo.history

import io.horizen.consensus._
import io.horizen.params.{NetworkParams, NetworkParamsUtils}
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.node.NodeHistory
import io.horizen.utxo.storage.SidechainHistoryStorage
import io.horizen.SidechainTypes
import io.horizen.history.AbstractHistory
import io.horizen.history.validation.{HistoryBlockValidator, SemanticBlockValidator}
import sparkz.util.{ModifierId, SparkzEncoding, SparkzLogging}

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

  override def tooManyBlocksWithoutMcHeaders(parentBlockId: ModifierId, noMcHeadersInCurrentBlock: Boolean, consensusEpochNumber: Int): Boolean = false
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
