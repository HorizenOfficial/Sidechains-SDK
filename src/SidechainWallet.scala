import scorex.core.VersionTag
import scorex.core.block.Block

import scala.util.Try

// 2 stores: one for Boxes(WalletBoxes), another for secrets
// TO DO: we need to wrap LSMStore

// TO DO: put also SidechainSecretsCompanion and SidechainBoxesCompanion with a data provided by Sidechain developer
case class SidechainWallet(seed: Array[Byte], boxStore: LSMStore, secretStore: LSMStore) extends Wallet[Secret,
                  ProofOfKnowledgeProposition[Secret],
                  BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]],
                  Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]],
                  SidechainWallet] {

  override type NVCT = this.type

  // 1) check for existence
  // 2) try to store in SecretStoreusing SidechainSecretsCompanion
  override def addSecret(secret: Secret): Try[SidechainWallet] = ???

  // 1) check for existence
  // 2) remove from SecretStore (note: provide a unique version to SecretStore)
  override def removeSecret(publicImage: ProofOfKnowledgeProposition[Secret]): Try[SidechainWallet] = ???

  override def secret(publicImage: ProofOfKnowledgeProposition[Secret]): Option[Secret] = ???

  // get all secrets, use SidechainSecretsCompanion to deserialize
  override def secrets(): Set[Secret] = ???

  // get all boxes as WalletBox object using SidechainBoxesCompanion
  override def boxes(): Seq[WalletBox[ProofOfKnowledgeProposition[Secret], _ <: Box[ProofOfKnowledgeProposition[Secret]]]] = ???

  // get all secrets using SidechainSecretsCompanion -> get .publicImage of each
  override def publicKeys(): Set[ProofOfKnowledgeProposition[Secret]] = ???

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(tx: BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]): SidechainWallet = this

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(txs: Seq[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): SidechainWallet = this

  // scan like in HybridApp, but in more general way.
  // update boxes in BoxStore
  override def scanPersistent(modifier: Block[BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]]): SidechainWallet = ???

  // rollback BoxStore only. SecretStore must not changed
  override def rollback(to: VersionTag): Try[SidechainWallet] = ???
}
