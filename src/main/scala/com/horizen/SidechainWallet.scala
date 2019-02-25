package com.horizen

import java.{lang, util}

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.node.NodeWallet
import com.horizen.proposition.{ProofOfKnowledgeProposition, Proposition}
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction
import scorex.core.VersionTag
import scorex.core.block.Block

import scala.util.Try

// 2 stores: one for Boxes(WalletBoxes), another for secrets
// TO DO: we need to wrap LSMStore

// TO DO: put also SidechainSecretsCompanion and SidechainBoxesCompanion with a data provided by Sidechain developer



case class SidechainWallet(seed: Array[Byte], boxStore: LSMStore, secretStore: LSMStore) extends Wallet[Secret,
                  SidechainTypes#P,
                  SidechainTypes#BT,
                  SidechainBlock,
                  SidechainWallet] with NodeWallet {

  override type NVCT = SidechainWallet

  // 1) check for existence
  // 2) try to store in SecretStoreusing SidechainSecretsCompanion
  override def addSecret(secret: Secret): Try[SidechainWallet] = ???

  // 1) check for existence
  // 2) remove from SecretStore (note: provide a unique version to SecretStore)
  override def removeSecret(publicImage: SidechainTypes#P): Try[SidechainWallet] = ???

  override def secret(publicImage: SidechainTypes#P): Option[Secret] = ???

  // get all secrets, use SidechainSecretsCompanion to deserialize
  override def secrets(): Set[Secret] = ???

  // get all boxes as WalletBox object using SidechainBoxesCompanion
  override def boxes(): Seq[WalletBox] = ???

  // get all secrets using SidechainSecretsCompanion -> get .publicImage of each
  override def publicKeys(): Set[SidechainTypes#P] = ???

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(tx: SidechainTypes#BT): SidechainWallet = this

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(txs: Seq[SidechainTypes#BT]): SidechainWallet = this

  // scan like in HybridApp, but in more general way.
  // update boxes in BoxStore
  override def scanPersistent(modifier: SidechainBlock): SidechainWallet = ???

  // rollback BoxStore only. SecretStore must not changed
  override def rollback(to: VersionTag): Try[SidechainWallet] = ???


  // Java NodeWallet interface definition
  override def allBoxes(): util.List[Box[_ <: Proposition]] = ???

  override def allBoxes(boxIdsToExclude: util.List[Array[Byte]]): util.List[Box[_ <: Proposition]] = ???

  override def boxesOfType(`type`: Class[_ <: Box[_ <: Proposition]]): util.List[Box[_ <: Proposition]] = ???

  override def boxesOfType(`type`: Class[_ <: Box[_ <: Proposition]], boxIdsToExclude: util.List[Array[Byte]]): util.List[Box[_ <: Proposition]] = ???

  override def secretByPublicImage(publicImage: ProofOfKnowledgeProposition[_ <: Secret]): Secret = ???

  override def allSecrets(): util.List[Secret] = ???

  override def secretsOfType(`type`: Class[_ <: Secret]): util.List[Secret] = ???
}
