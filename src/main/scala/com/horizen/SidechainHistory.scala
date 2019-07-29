package com.horizen

import java.util
import java.util.Optional

import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import scorex.util.{ModifierId, bytesToId, idToBytes}
import scorex.core.consensus.History.ModifierIds
import scorex.core.consensus.{History, ModifierSemanticValidity, SyncInfo}

import scala.util.Try
import com.horizen.node.NodeHistory
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.transaction.Transaction

import scala.collection.JavaConverters

// TO DO: implement it like in HybridHistory
// TO DO: think about additional methods (consensus related?)


class SidechainHistory()
  extends scorex.core.consensus.History[
      SidechainBlock,
      SyncInfo,
      SidechainHistory] with NodeHistory {

  override type NVCT = SidechainHistory

  override def append(modifier: SidechainBlock): Try[(SidechainHistory, History.ProgressInfo[SidechainBlock])] = ???

  override def reportModifierIsValid(modifier: SidechainBlock): SidechainHistory = ???

  override def reportModifierIsInvalid(modifier: SidechainBlock, progressInfo: History.ProgressInfo[SidechainBlock]): (SidechainHistory, History.ProgressInfo[SidechainBlock]) = ???

  override def isEmpty: Boolean = ???

  override def applicableTry(modifier: SidechainBlock): Try[Unit] = ???

  override def modifierById(modifierId: ModifierId): Option[SidechainBlock] = ???

  override def isSemanticallyValid(modifierId: ModifierId): ModifierSemanticValidity = ???

  override def openSurfaceIds(): Seq[ModifierId] = ???

  override def continuationIds(info: SyncInfo, size: Int): Option[ModifierIds] = ???

  override def syncInfo: SyncInfo = ???

  override def compare(other: SyncInfo): History.HistoryComparisonResult = ???

  override def getBlockById(blockId: String): Optional[SidechainBlock] = {
    Optional.ofNullable(
      modifierById(
        bytesToId(blockId.getBytes)).orNull
    )
  }

  /**
    * Get the id of the previous block from a given block id
    */
  private def previousBlockId(blockId : ModifierId) : Option[ModifierId] = {
    // TO-DO. Modify on base of our SDK
    def storage_parentBlockId(blockId : ModifierId) : Option[ModifierId] = ???
    modifierById(blockId).getOrElse() match {
      case Some(block) => storage_parentBlockId(blockId)
      case _ => None
    }
  }

  override def getLastBlockids(startBlock: SidechainBlock, count: Int): util.List[String] = {
    // TO-DO. Modify on base of our SDK
    def isGenesisBlock_(blockId: ModifierId): Boolean = ???
    // chainBack should return maximum count elements
    def chainBack_(block : SidechainBlock, until : ModifierId => Boolean, limit : Int) : Option[Seq[ModifierId]] = ???
    var seqOfBlockIds = chainBack_(startBlock, isGenesisBlock_, count).get
    if (seqOfBlockIds.length < count){
      seqOfBlockIds = previousBlockId(seqOfBlockIds.head).get +: seqOfBlockIds
    }

    JavaConverters.seqAsJavaList(seqOfBlockIds.map(id => idToBytes(id).toString))
  }

  override def getBestBlock(): SidechainBlock = ???

  override def getBlockIdByHeight(height: Int): String = {
    // this should use (when ready) the optimized indexed version of history
    // TO-DO. Modify on base of our SDK
    def isGenesisBlock_(blockId: ModifierId): Boolean = ???
    val bestBlock : SidechainBlock = ???
    // chainBack should return maximum count elements
    def chainBack_(block : SidechainBlock, until : ModifierId => Boolean, limit : Int) : Option[Seq[ModifierId]] = ???
    var seqOfBlockIds = chainBack_(bestBlock, isGenesisBlock_, height).get
    var blockId : ModifierId =
      if(height == 1){
        var headBlockId = seqOfBlockIds.headOption.get
        previousBlockId(headBlockId).get
      }
      else
        seqOfBlockIds(height -2)

    idToBytes(blockId).toString
  }

  override def getCurrentHeight(): Int = ???

  override def searchTransactionInsideSidechainBlock(transactionId: String, blockId: String): Optional[Transaction] = {
    // Just as reminder. history.storage.modifierById
    Optional.empty()
  }

  override def searchTransactionInsideBlockchain(transactionId: String): Optional[Transaction] = {
    // Just as reminder
    Optional.empty()
  }

  override def getSidechainBlockByMainchainBlockReferenceHash(mcBlockReferenceHash: Array[Byte]): Optional[SidechainBlock] = ???

  override def getHeightOfMainchainBlock(mcBlockReferenceHash: Array[Byte]): Int = ???

  override def getMainchainBlockReferenceByHash(mainchainBlockReferenceHash: Array[Byte]): MainchainBlockReference = ???

  override def getBestMainchainBlockReferenceInfo: MainchainBlockReferenceInfo = {
    // best MC block header which has already been included in a SC block
    def getBestMCBlockHeaderIncludedInSCBlock : MainchainBlockReference = ???
    var mcBlockReference = getBestMCBlockHeaderIncludedInSCBlock
    var hashOfMcBlockReference = mcBlockReference.hash

    var height = getHeightOfMainchainBlock(hashOfMcBlockReference)

    // Sidechain block which contains this MC block reference
    var scBlock = getSidechainBlockByMainchainBlockReferenceHash(hashOfMcBlockReference)
    var scBlockId = Array[Byte]()
    if(scBlock.isPresent)
      scBlockId = idToBytes(scBlock.get().id)

    new MainchainBlockReferenceInfo(
      hashOfMcBlockReference,
      height,
      scBlockId
    )
  }

  override def createMainchainBlockReference(mainchainBlockData: Array[Byte]): Try[MainchainBlockReference] = ???

}