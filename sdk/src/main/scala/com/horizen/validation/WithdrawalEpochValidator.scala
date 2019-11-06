package com.horizen.validation

import com.horizen.block.SidechainBlock
import com.horizen.params.NetworkParams
import com.horizen.SidechainHistory
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.BytesUtils
import scorex.util.idToBytes

import scala.util.{Failure, Success, Try}

class WithdrawalEpochValidator(params: NetworkParams) extends SidechainBlockValidator {
  override def validate(block: SidechainBlock, history: SidechainHistory): Try[Unit] = {
    if (block.id.equals(params.sidechainGenesisBlockId)) {
      // Verify that block contains only 1 MC block reference with a valid Sidechain Creation info
      if(block.mainchainBlocks.size != 1)
        return Failure(new IllegalArgumentException("Sidechain block validation failed for %s: genesis block should contain single MC block reference.".format(BytesUtils.toHexString(idToBytes(block.id)))))

      val sidechainCreation = block.mainchainBlocks.head.sidechainRelatedAggregatedTransaction.get.mc2scTransactionsOutputs.get(0).asInstanceOf[SidechainCreation]
      if(sidechainCreation.withdrawalEpochLength() != params.withdrawalEpochLength)
        return Failure(new IllegalArgumentException("Sidechain block validation failed for %s: genesis block contains different withdrawal epoch length than expected in configs.".format(BytesUtils.toHexString(idToBytes(block.id)))))
    }
    // TO DO: do we need to check that non-genesis SC blocks don't contain MC block references with AggTx with SidechainCreation outputs?
    // or we can relay on MC consensus which forbids to declare the same SC id twice?

    history.storage.blockInfoById(block.parentId) match {
      case Some(parentBlockInfo) => // Parent block is present
        if(parentBlockInfo.withdrawalEpochIndex < params.withdrawalEpochLength) { // Parent block is in the middle of Epoch
          val blockWithdrawalEpochIndex: Int = parentBlockInfo.withdrawalEpochIndex + block.mainchainBlocks.size
          if(blockWithdrawalEpochIndex < params.withdrawalEpochLength) // Block is in the middle of Epoch
            Success()
          else if(blockWithdrawalEpochIndex > params.withdrawalEpochLength) // Block contains MC Block refs from different Epochs
            Failure(new IllegalArgumentException("Sidechain block %s contains MC Block references, that belong to different withdrawal epochs.".format(BytesUtils.toHexString(idToBytes(block.id)))))
          else { // Last SC block in the Epoch
            if(block.transactions.isEmpty)
              Success()
            else
              Failure(new IllegalArgumentException("Sidechain block %s is the last withdrawal epoch block, but contains Transactions.".format(BytesUtils.toHexString(idToBytes(block.id)))))
          }
        }
        else // Parent block is the last block in the Epoch, so current block is the first one of the next Epoch.
          Success()

      case None => // Parent block is missed
        Success() // Let it be processed inside history append logic.
    }
  }
}
