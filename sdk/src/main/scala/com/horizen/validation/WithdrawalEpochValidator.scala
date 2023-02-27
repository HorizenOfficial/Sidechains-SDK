package com.horizen.validation

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.params.NetworkParams
import com.horizen.AbstractHistory
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.cryptolibprovider.{CircuitTypes, CommonCircuit}
import CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{BlockUtils, BytesUtils, WithdrawalEpochUtils}
import sparkz.util.idToBytes

import scala.util.Try

class WithdrawalEpochValidator[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
  HT <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HT]
]
(
  params: NetworkParams
)
  extends HistoryBlockValidator[TX, H, PMOD, FPI, HSTOR, HT] {

  override def validate(block: PMOD, history: HT): Try[Unit] = Try {
    if (block.id.equals(params.sidechainGenesisBlockId))
      validateGenesisBlock(block).get
    else
      validateBlock(block, history).get
  }


  private def validateGenesisBlock(block: PMOD): Try[Unit] = Try {
    // Verify that block contains only 1 MC block reference data with a valid Sidechain Creation info
    if(block.mainchainBlockReferencesData.size != 1)
      throw new IllegalArgumentException("Sidechain block validation failed for %s: genesis block should contain single MC block reference.".format(BytesUtils.toHexString(idToBytes(block.id))))


    val sidechainCreation = BlockUtils.tryGetSidechainCreation(block).get
    if(!params.isNonCeasing && sidechainCreation.withdrawalEpochLength() != params.withdrawalEpochLength)
      throw new IllegalArgumentException("Sidechain block validation failed for %s: genesis block contains different withdrawal epoch length than expected in configs.".format(BytesUtils.toHexString(idToBytes(block.id))))

    // Check that sidechain declares proper number of custom fields
    val expectedNumOfCustomFields = params.circuitType match {
      case NaiveThresholdSignatureCircuit =>
        params.isCSWEnabled match {
          case true =>
            CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW
          case false =>
            CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_NO_KEY_ROTATION
        }
      case NaiveThresholdSignatureCircuitWithKeyRotation =>
        CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION
    }

    if(sidechainCreation.getScCrOutput.fieldElementCertificateFieldConfigs.size != expectedNumOfCustomFields) {
        throw new IllegalArgumentException(s"Sidechain block validation failed for ${BytesUtils.toHexString(idToBytes(block.id))}: " +
          "genesis block declares sidechain with different number of custom field configs. " +
          s"Actual: ${sidechainCreation.getScCrOutput.fieldElementCertificateFieldConfigs.size}, expected $expectedNumOfCustomFields")
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

  private def validateBlock(block: PMOD, history: HT): Try[Unit] = Try {
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
        val blockEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, parentBlockInfo.withdrawalEpochInfo, params)
        if (blockEpochInfo.epoch > parentBlockInfo.withdrawalEpochInfo.epoch) { // epoch increased
          if (parentBlockInfo.withdrawalEpochInfo.lastEpochIndex != params.withdrawalEpochLength) // parent index was not the last index of the block -> Block contains MC Block refs from different Epochs
            throw new IllegalArgumentException("Sidechain block %s contains MC Block references, that belong to different withdrawal epochs.".format(BytesUtils.toHexString(idToBytes(block.id))))

        } else { // epoch is the same
          // Block is the last block of the withdrawal epoch and contains SC2SC Txs.
          // Note: MC2SCAggTx is allowed, because of being a part of MC block reference data.
          if (blockEpochInfo.lastEpochIndex == params.withdrawalEpochLength && block.sidechainTransactions.nonEmpty)
            throw new IllegalArgumentException("Sidechain block %s is the withdrawal epoch last block, but contains Sidechain Transactions.".format(BytesUtils.toHexString(idToBytes(block.id))))
        }

      case None =>
        throw new IllegalArgumentException("Sidechain block %s parent block is missed.".format(BytesUtils.toHexString(idToBytes(block.id))))
    }
  }
}
