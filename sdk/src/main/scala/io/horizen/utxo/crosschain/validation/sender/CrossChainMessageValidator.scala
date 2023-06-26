package io.horizen.utxo.crosschain.validation.sender

import io.horizen.SidechainTypes
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.fork.Sc2ScFork
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.CrossChainMessageHash
import io.horizen.transaction.MC2SCAggregatedTransaction
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.box.CrossChainMessageBox
import io.horizen.utxo.crosschain.CrossChainValidator
import io.horizen.utxo.state.SidechainState
import io.horizen.utxo.storage.SidechainStateStorage

import scala.jdk.CollectionConverters.asScalaBufferConverter

class CrossChainMessageValidator(
                                  scStateStorage: SidechainStateStorage,
                                  scState: SidechainState,
                                  networkParams: NetworkParams
                                ) extends CrossChainValidator[SidechainBlock] {
  override def validate(scBlock: SidechainBlock): Unit = {
    validateNoMoreThanOneMessagePerBlock(scBlock)

    scBlock.transactions.foreach(tx => validateNotDuplicateMessage(tx))
  }

  private def validateNoMoreThanOneMessagePerBlock(scBlock: SidechainBlock): Unit = {
    val allCrossMessagesBox = scBlock.transactions.flatMap(tx => tx.newBoxes().asScala.filter(box => box.isInstanceOf[CrossChainMessageBox]))
    if (allCrossMessagesBox.nonEmpty) {
      val sc2ScFork = Sc2ScFork.get(scStateStorage.getConsensusEpochNumber.getOrElse(0))

      if (!sc2ScFork.sc2ScCanSend) {
        throw new IllegalArgumentException("CrossChainMessages not allowed in this sidechain")
      } else {
        val allCrossMessagesHashes = scala.collection.mutable.Seq[CrossChainMessageHash]()
        allCrossMessagesBox.foreach(box => {
          val ccMsg = SidechainState.buildCrosschainMessageFromUTXO(box.asInstanceOf[CrossChainMessageBox], networkParams)
          val currentHash = ccMsg.getCrossChainMessageHash
          if (allCrossMessagesHashes.contains(currentHash)) {
            throw new IllegalArgumentException(s"Block ${scBlock.id} contains duplicated CrossChainMessageBox")
          } else {
            allCrossMessagesHashes :+ currentHash
          }
        })
        //crosschain messages validation: check max number of boxes per epoch
        checkCrosschainMessagesBoxesAllowed(scBlock.mainchainBlockReferencesData.size, allCrossMessagesBox.size)
      }
    }
  }

  private def checkCrosschainMessagesBoxesAllowed(mainchainBlockReferencesInBlock: Int, boxesInBlock: Int): Unit = {
    val alreadyMined = scState.getAlreadyMinedCrossChainMessagesInCurrentEpoch
    val allowed = scState.getAllowedCrossChainMessageBoxes(mainchainBlockReferencesInBlock, CryptoLibProvider.sc2scCircuitFunctions.getMaxCrossChainMessagesPerEpoch)
    val total = alreadyMined + boxesInBlock
    if (total > allowed) {
      throw new IllegalStateException("Exceeded the maximum number of CrosschainMessages allowed!")
    }
  }

  private def validateNotDuplicateMessage(tx: SidechainTypes#SCBT): Unit = {
    // Do we really need this check since we're filtering the boxes instances of CrossChainMessageBox??
    if (!tx.isInstanceOf[MC2SCAggregatedTransaction]) {
      val ccBoxes = tx.newBoxes().asScala.filter(box => box.isInstanceOf[CrossChainMessageBox])
      val sc2ScFork = Sc2ScFork.get(scStateStorage.getConsensusEpochNumber.getOrElse(0))

      if (!sc2ScFork.sc2ScCanSend && ccBoxes.nonEmpty) {
        throw new Exception(s"CrossChainMessages not allowed in this sidechain")
      } else {
        ccBoxes.foreach(cmBox => {
          val ccMsg = SidechainState.buildCrosschainMessageFromUTXO(cmBox.asInstanceOf[CrossChainMessageBox], networkParams)
          val messageHash = ccMsg.getCrossChainMessageHash

          if (scState.getCrossChainMessageHashEpoch(messageHash).isDefined) {
            throw new Exception("CrossChainMessage already found in state")
          }
        })
      }
    }
  }
}
