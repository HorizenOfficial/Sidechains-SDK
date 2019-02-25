package com.horizen

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.proposition.ProofOfKnowledgeProposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction
import scorex.util.ModifierId
import scorex.core.block.Block
import scorex.core.consensus.History.ModifierIds
import scorex.core.consensus.{History, ModifierSemanticValidity, SyncInfo}

import scala.util.Try

// TO DO: implement it like in HybridHistory
// TO DO: think about additional methods (consensus related?)


class SidechainHistory
  extends scorex.core.consensus.History[
      SidechainBlock,
      SyncInfo,
      SidechainHistory] {

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
}
