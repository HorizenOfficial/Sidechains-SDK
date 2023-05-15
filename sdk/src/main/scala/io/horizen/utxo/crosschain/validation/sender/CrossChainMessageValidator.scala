package io.horizen.utxo.crosschain.validation.sender

import io.horizen.SidechainTypes
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.{CrossChainMessageHash, Sc2ScConfigurator}
import io.horizen.transaction.MC2SCAggregatedTransaction
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.box.CrossChainMessageBox
import io.horizen.utxo.crosschain.CrossChainValidator
import io.horizen.utxo.state.SidechainState

import scala.jdk.CollectionConverters.asScalaBufferConverter

class CrossChainMessageValidator(
                                  sc2ScConfig: Sc2ScConfigurator,
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
      if (!sc2ScConfig.canSendMessages) {
        throw new IllegalArgumentException("CrossChainMessages not allowed in this sidechain")
      } else {
        val allCrossMessagesHashes = scala.collection.mutable.Seq[CrossChainMessageHash]()
        allCrossMessagesBox.foreach(box => {
          val currentHash = CryptoLibProvider.sc2scCircuitFunctions.getCrossChainMessageHash(SidechainState.buildCrosschainMessageFromUTXO(box.asInstanceOf[CrossChainMessageBox], networkParams))
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

      if (!sc2ScConfig.canSendMessages && ccBoxes.nonEmpty) {
        throw new Exception(s"CrossChainMessages not allowed in this sidechain")
      } else {
        ccBoxes.foreach(cmBox => {
          val messageHash =
            CryptoLibProvider
              .sc2scCircuitFunctions
              .getCrossChainMessageHash(SidechainState.buildCrosschainMessageFromUTXO(cmBox.asInstanceOf[CrossChainMessageBox], networkParams))

          if (scState.getCrossChainMessageHashEpoch(messageHash).isDefined) {
            throw new Exception("CrossChainMessage already found in state")
          }
        })
      }
    }
  }
}
