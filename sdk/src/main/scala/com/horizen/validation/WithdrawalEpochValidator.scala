package com.horizen.validation

import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams
import com.horizen.SidechainHistory
import com.horizen.cryptolibprovider.CommonCircuit
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BytesUtils, WithdrawalEpochUtils}
import scorex.util.idToBytes

import scala.util.Try

class WithdrawalEpochValidator(params: NetworkParams) extends HistoryBlockValidator {
  override def validate(block: SidechainBlock, history: SidechainHistory): Try[Unit] = Try {
    if (block.id.equals(params.sidechainGenesisBlockId))
      validateGenesisBlock(block).get
    else
      validateBlock(block, history).get
  }


  private def validateGenesisBlock(block: SidechainBlock): Try[Unit] = Try {
    // Verify that block contains only 1 MC block reference data with a valid Sidechain Creation info
    if(block.mainchainBlockReferencesData.size != 1)
      throw new IllegalArgumentException("Sidechain block validation failed for %s: genesis block should contain single MC block reference.".format(BytesUtils.toHexString(idToBytes(block.id))))

    val sidechainCreation = block.mainchainBlockReferencesData.head.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs.get(0).asInstanceOf[SidechainCreation]
    if(sidechainCreation.withdrawalEpochLength() != params.withdrawalEpochLength)
      throw new IllegalArgumentException("Sidechain block validation failed for %s: genesis block contains different withdrawal epoch length than expected in configs.".format(BytesUtils.toHexString(idToBytes(block.id))))

    // Check that sidechain supports CSWs
    if(sidechainCreation.getScCrOutput.ceasedVkOpt.isEmpty) {
      throw new IllegalArgumentException(s"Sidechain block validation failed for ${BytesUtils.toHexString(idToBytes(block.id))}: " +
        "genesis block declares sidechain without CSW support.")
    }

    // Check that sidechain declares proper number of custom fields
    if(sidechainCreation.getScCrOutput.fieldElementCertificateFieldConfigs.size != CommonCircuit.customFieldsNumber) {
      throw new IllegalArgumentException(s"Sidechain block validation failed for ${BytesUtils.toHexString(idToBytes(block.id))}: " +
        "genesis block declares sidechain with different number of custom field configs.")
    }

    // Check that sidechain declares no custom bitvectors
    if(sidechainCreation.getScCrOutput.bitVectorCertificateFieldConfigs.nonEmpty) {
      throw new IllegalArgumentException(s"Sidechain block validation failed for ${BytesUtils.toHexString(idToBytes(block.id))}: " +
        "genesis block declares sidechain with custom bit vectors.")
    }

    // Check that sidechain declares no MBTR support
    if(sidechainCreation.getScCrOutput.mainchainBackwardTransferRequestDataLength != 0) {
      throw new IllegalArgumentException(s"Sidechain block validation failed for ${BytesUtils.toHexString(idToBytes(block.id))}: " +
        "genesis block declares sidechain with MBTR support.")
    }
  }

  private def validateBlock(block: SidechainBlock, history: SidechainHistory): Try[Unit] = Try {
    for(data <- block.mainchainBlockReferencesData) {
      data.sidechainRelatedAggregatedTransaction match {
        case Some(aggTx) =>
          if(aggTx.mc2scTransactionsOutputs().stream().anyMatch(output => output.isInstanceOf[SidechainCreation]))
            throw new IllegalArgumentException("Sidechain block validation failed for %s: non-genesis block contains Sidechain Creation output.".format(BytesUtils.toHexString(idToBytes(block.id))))
        case None =>
      }
    }

    history.storage.blockInfoOptionById(block.parentId) match {
      case Some(parentBlockInfo) => // Parent block is present
        val blockEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(block, parentBlockInfo.withdrawalEpochInfo, params)
        if (blockEpochInfo.epoch > parentBlockInfo.withdrawalEpochInfo.epoch) { // epoch increased
          if (parentBlockInfo.withdrawalEpochInfo.lastEpochIndex != params.withdrawalEpochLength) // parent index was not the last index of the block -> Block contains MC Block refs from different Epochs
            throw new IllegalArgumentException("Sidechain block %s contains MC Block references, that belong to different withdrawal epochs.".format(BytesUtils.toHexString(idToBytes(block.id))))

        } else { // epoch is the same
          if (blockEpochInfo.lastEpochIndex == params.withdrawalEpochLength && block.transactions.nonEmpty) // Block is the last block of the epoch and contains SC Txs
            throw new IllegalArgumentException("Sidechain block %s is the last withdrawal epoch block, but contains Sidechain Transactions.".format(BytesUtils.toHexString(idToBytes(block.id))))
        }

      case None =>
        throw new IllegalArgumentException("Sidechain block %s parent block is missed.".format(BytesUtils.toHexString(idToBytes(block.id))))
    }

    val backwardTransferCertificateCount = block.mainchainBlockReferencesData.flatMap(_.topQualityCertificate).size
  }
}
