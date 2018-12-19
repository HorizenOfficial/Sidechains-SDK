package com.horizen

import com.horizen.box.Box
import com.horizen.proposition.ProofOfKnowledgeProposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction
import scorex.core.ModifierId
import scorex.core.block.Block
import scorex.core.consensus.History.ModifierIds
import scorex.core.consensus.{History, ModifierSemanticValidity, SyncInfo}

import scala.util.Try

// TO DO: implement it like in HybridHistory
// TO DO: think about additional methods (consensus related?)

class SidechainHistory extends scorex.core.consensus.History[
      Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]],
      SyncInfo,
      SidechainHistory] {
  override def append(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): Try[(SidechainHistory, History.ProgressInfo[Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]])] = ???

  override def reportModifierIsValid(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): SidechainHistory = ???

  override def reportModifierIsInvalid(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]], progressInfo: History.ProgressInfo[Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]]): (SidechainHistory, History.ProgressInfo[Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]]) = ???

  override def isEmpty: Boolean = ???

  override def applicableTry(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): Try[Unit] = ???

  override def modifierById(modifierId: ModifierId): Option[Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]] = ???

  override def isSemanticallyValid(modifierId: ModifierId): ModifierSemanticValidity = ???

  override def openSurfaceIds(): Seq[ModifierId] = ???

  override def continuationIds(info: SyncInfo, size: Int): Option[ModifierIds] = ???

  override def syncInfo: SyncInfo = ???

  override def compare(other: SyncInfo): History.HistoryComparisonResult = ???

  override type NVCT = this.type
}
