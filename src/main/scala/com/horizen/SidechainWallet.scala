package com.horizen

import java.util.{Optional => JOptional, List => JList}

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.wallet.ApplicationWallet
import com.horizen.node.NodeWallet
import com.horizen.proposition.Proposition
import com.horizen.proposition.ProofOfKnowledgeProposition
import com.horizen.secret.Secret
import com.horizen.storage.{SidechainSecretStorage, SidechainWalletBoxStorage}
import com.horizen.transaction.Transaction
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import scorex.core.VersionTag
import scala.util.Try
import scala.collection.JavaConverters._


trait Wallet[S <: Secret, P <: Proposition, TX <: Transaction, PMOD <: scorex.core.PersistentNodeViewModifier, W <: Wallet[S, P, TX, PMOD, W]]
  extends scorex.core.transaction.wallet.Vault[TX, PMOD, W] {
  self: W =>

  def addSecret(secret: S): Try[W]

  def removeSecret(publicImage: P): Try[W]

  def secret(publicImage: P): Option[S]

  def secrets(): Set[S]

  def boxes(): Seq[WalletBox]

  def publicKeys(): Set[P]
}

class SidechainWallet(walletBoxStorage: SidechainWalletBoxStorage, secretStorage: SidechainSecretStorage,
                     applicationWallet: ApplicationWallet)
  extends Wallet[Secret,
                 ProofOfKnowledgeProposition[_ <: Secret],
                 SidechainTypes#BT,
                 SidechainBlock,
                 SidechainWallet]
  with NodeWallet
{
  override type NVCT = SidechainWallet

  require(applicationWallet != null, "ApplicationWallet must be NOT NULL.")

  // 1) check for existence
  // 2) try to store in SecretStore using SidechainSecretsCompanion
  override def addSecret(secret: Secret): Try[SidechainWallet] = Try {
    require(secret != null, "Secret must be NOT NULL.")
    secretStorage.add(secret)
    applicationWallet.onAddSecret(secret)
    this
  }

  // 1) check for existence
  // 2) remove from SecretStore (note: provide a unique version to SecretStore)
  override def removeSecret(publicImage: ProofOfKnowledgeProposition[_ <: Secret]): Try[SidechainWallet] = Try {
    require(publicImage != null, "PublicImage must be NOT NULL.")
    secretStorage.remove(publicImage)
    applicationWallet.onRemoveSecret(publicImage)
    this
  }

  override def secret(publicImage: ProofOfKnowledgeProposition[_ <: Secret]): Option[Secret] = {
    secretStorage.get(publicImage)
  }

  override def secrets(): Set[Secret] = {
    secretStorage.getAll.toSet
  }

  override def boxes(): Seq[WalletBox] = {
    walletBoxStorage.getAll
  }

  override def publicKeys(): Set[ProofOfKnowledgeProposition[_ <: Secret]] = {
    secretStorage.getAll.map(_.publicImage()).toSet
  }

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(tx: SidechainTypes#BT): SidechainWallet = this

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(txs: Seq[SidechainTypes#BT]): SidechainWallet = this

  // scan like in HybridApp, but in more general way.
  // update boxes in BoxStore
  override def scanPersistent(modifier: SidechainBlock): SidechainWallet = {
    //require(modifier != null, "SidechainBlock must be NOT NULL.")
    val changes = SidechainState.changes(modifier).get
    val pubKeys = publicKeys().map(_.asInstanceOf[Proposition])

    val newBoxes = changes.toAppend.filter(s => pubKeys.contains(s.box.proposition()))
        .map(_.box)
        .map { box =>
               val boxTransaction = modifier.transactions.find(t => t.newBoxes().asScala.exists(tb => java.util.Arrays.equals(tb.id, box.id)))
               val txId = boxTransaction.map(_.id).get
               val ts = boxTransaction.map(_.timestamp).getOrElse(modifier.timestamp)
               new WalletBox(box, txId, ts)
    }

    val boxIdsToRemove = changes.toRemove.map(_.boxId.array)
    walletBoxStorage.update(new ByteArrayWrapper(BytesUtils.fromHexString(modifier.id)), newBoxes.toList, boxIdsToRemove.toList).get

    applicationWallet.onChangeBoxes(newBoxes.map(_.box.asInstanceOf[Box[_ <: Proposition]]).toList.asJava,
      boxIdsToRemove.toList.asJava)

    this
  }

  // rollback BoxStore only. SecretStore must not changed
  override def rollback(to: VersionTag): Try[SidechainWallet] = Try {
    require(to != null, "Version to rollback to must be NOT NULL.")
    walletBoxStorage.rollback(new ByteArrayWrapper(BytesUtils.fromHexString(to)))
    this
  }

  // Java NodeWallet interface definition
  override def allBoxes : JList[Box[_ <: Proposition]] = {
    walletBoxStorage.getAll.map(_.box).asJava
  }

  override def allBoxes(boxIdsToExclude: JList[Array[Byte]]): JList[Box[_ <: Proposition]] = {
    walletBoxStorage.getAll
      .filter((wb : WalletBox) => !BytesUtils.contains(boxIdsToExclude, wb.box.id()))
      .map(_.box)
      .asJava
  }

  override def boxesOfType(boxType: Class[_ <: Box[_ <: Proposition]]): JList[Box[_ <: Proposition]] = {
    walletBoxStorage.getByType(boxType)
      .map(_.box)
      .asJava
  }

  override def boxesOfType(boxType: Class[_ <: Box[_ <: Proposition]], boxIdsToExclude: JList[Array[Byte]]): JList[Box[_ <: Proposition]] = {
    walletBoxStorage.getByType(boxType)
      .filter((wb : WalletBox) => !BytesUtils.contains(boxIdsToExclude, wb.box.id()))
      .map(_.box)
      .asJava
  }

  override def boxesBalance(boxType: Class[_ <: Box[_ <: Proposition]]): java.lang.Long = {
    walletBoxStorage.getBoxesBalance(boxType)
  }

  override def secretByPublicKey(publicKey: ProofOfKnowledgeProposition[_ <: Secret]): JOptional[Secret] = {
    JOptional.ofNullable(secretStorage.get(publicKey).orNull)
  }

  override def allSecrets(): JList[Secret] = {
    secretStorage.getAll.asJava
  }

  override def secretsOfType(secretType: Class[_ <: Secret]): JList[Secret] = {
    secretStorage.getAll.filter(_.getClass.equals(secretType)).asJava
  }

}
