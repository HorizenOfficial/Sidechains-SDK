import scorex.core.ModifierId
import scorex.core.block.Block
import scorex.core.consensus.History.ModifierIds
import scorex.core.consensus.{History, ModifierSemanticValidity, SyncInfo}

import scala.util.Try

// TO DO: implement it like in HybridHistory
// TO DO: think about additional methods (consensus related?)

class SDKHistory extends scorex.core.consensus.History[
      Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]],
      SyncInfo,
      SDKHistory] {
  override def append(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): Try[(SDKHistory, History.ProgressInfo[Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]])] = ???

  override def reportModifierIsValid(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): SDKHistory = ???

  override def reportModifierIsInvalid(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]], progressInfo: History.ProgressInfo[Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]]): (SDKHistory, History.ProgressInfo[Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]]) = ???

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
