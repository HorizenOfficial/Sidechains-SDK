package io.horizen.history.validation

import io.horizen.block.{MainchainBlockReference, MainchainHeader, SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.{AbstractFeePaymentsInfo, MainchainHeaderHash, SidechainBlockInfo, byteArrayToMainchainHeaderHash}
import io.horizen.history.AbstractHistory
import io.horizen.params.NetworkParams
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.Transaction
import sparkz.util.ModifierId

import scala.util.{Failure, Success, Try}
import scala.util.control.Breaks._

class MainchainBlockReferenceValidator[
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
      validateGenesisBlock(block)
    else
      validateBlock(block, history)
  }

  private def validateGenesisBlock(verifiedBlock: PMOD): Unit = {
    if(verifiedBlock.mainchainHeaders.size != 1)
      throw new InvalidMainchainDataException(s"Genesis block expect to contain only 1 MainchainHeader, instead contains ${verifiedBlock.mainchainHeaders.size}.")

    if(verifiedBlock.mainchainBlockReferencesData.size != 1)
      throw new InvalidMainchainDataException(s"Genesis block expect to contain only 1 MainchainBlockReferenceData, instead contains ${verifiedBlock.mainchainBlockReferencesData.size}.")

    val reference: MainchainBlockReference = MainchainBlockReference(verifiedBlock.mainchainHeaders.head, verifiedBlock.mainchainBlockReferencesData.head)
    reference.semanticValidity(params) match {
      case Success(_) =>
      case Failure(e) => throw e
    }
  }

  // Note: We relay on the fact that previous blocks MainchainBlockReferenceData was checked.
  // Example:
  // SC block:    100     101     102       103       104
  // McHeaders:   10      11,12   13,14,15  -         16
  // McData:      10      -       11        12,13     14
  private def validateBlock(verifiedBlock: PMOD, history: HT): Unit = {
    if(verifiedBlock.mainchainBlockReferencesData.isEmpty)
      return

    // Loop back in history and found MainchainHeaders that wait for MainchainBlockReferenceData to be included.
    var missedMainchainReferenceDataHeaderHashesInfo: Seq[(MainchainHeaderHash, ModifierId)] = Seq()

    breakable {
      var blockId: ModifierId = verifiedBlock.parentId
      var bestMainchainReferenceDataHeaderHashOpt: Option[MainchainHeaderHash] = None
      while (true) {
        val blockInfo: SidechainBlockInfo = history.blockInfoById(blockId)
        // Prepend the list with MainchainHeaders info (hash and containing SidechainBlock id)
        missedMainchainReferenceDataHeaderHashesInfo = blockInfo.mainchainHeaderHashes.map((_, blockId)) ++ missedMainchainReferenceDataHeaderHashesInfo

        // Try to get the last MainchainReferenceData HeaderHash included into the chain
        if(bestMainchainReferenceDataHeaderHashOpt.isEmpty)
          bestMainchainReferenceDataHeaderHashOpt = blockInfo.mainchainReferenceDataHeaderHashes.lastOption

        if(bestMainchainReferenceDataHeaderHashOpt.isDefined) {
          // Go back in history until found MainchainHeader corresponding to the best known MainchainReferenceData
          val index: Int = missedMainchainReferenceDataHeaderHashesInfo.indexWhere(info => info._1 == bestMainchainReferenceDataHeaderHashOpt.get)
          if (index != -1) {
            // Drop the Headers info till best known MainchainReferenceData HeaderHash
            missedMainchainReferenceDataHeaderHashesInfo = missedMainchainReferenceDataHeaderHashesInfo.drop(index + 1)
            break
          }
        }
        blockId = blockInfo.parentId
      }
    }

    // Update header hashes info with verified block MainchainHeaders info
    missedMainchainReferenceDataHeaderHashesInfo =
      missedMainchainReferenceDataHeaderHashesInfo ++ verifiedBlock.mainchainHeaders.map(header => (byteArrayToMainchainHeaderHash(header.hash), verifiedBlock.id))

    // Block MainchainBlockReferencesData number must be not greater, then missed ones till verified block
    if(verifiedBlock.mainchainBlockReferencesData.size > missedMainchainReferenceDataHeaderHashesInfo.size)
      throw new InvalidMainchainDataException("Block contains more MainchainBlockReferenceData, than expected in the chain.")

    // Collect MainchainHeaders with corresponding MainchainReferenceData into MainchainReferences and verify them.
    verifiedBlock.mainchainBlockReferencesData.zip(missedMainchainReferenceDataHeaderHashesInfo).foldLeft(verifiedBlock){
      case (lastRetrievedBlock, (referenceData, (mainchainHeaderHash, containingBlockId))) =>
        // Check that header hash and data hash are the same.
        if(!referenceData.headerHash.sameElements(mainchainHeaderHash.data))
          throw new InvalidMainchainDataException("MainchainBlockReferenceData header hash and MainchainHeader hash are different.")

        val blockWithMainchainHeader: PMOD = containingBlockId match {
          case lastRetrievedBlock.id => lastRetrievedBlock  // not to extract from Storage full SidechainBlock again
          case verifiedBlock.id => verifiedBlock // it means that both header and data are present inside verified block
          case id => history.getBlockById(id).get
        }

        val mainchainHeader: MainchainHeader = blockWithMainchainHeader.mainchainHeaders.find(header => header.hash.sameElements(mainchainHeaderHash.data)).get
        val reference: MainchainBlockReference = MainchainBlockReference(mainchainHeader, referenceData)
        reference.semanticValidity(params) match {
          case Success(_) =>
          case Failure(e) => throw e
        }
        blockWithMainchainHeader
    }
  }
}
