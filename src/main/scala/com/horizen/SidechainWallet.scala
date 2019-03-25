package com.horizen

import java.lang
import java.util.{List => JList}
import java.util.Optional

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.wallet.ApplicationWallet
import com.horizen.node.NodeWallet
import com.horizen.proposition.Proposition
import com.horizen.proposition.ProofOfKnowledgeProposition
import com.horizen.secret.Secret
import com.horizen.storage.{SidechainSecretStorage, SidechainWalletBoxStorage}
import com.horizen.transaction.{BoxTransaction, Transaction}
import com.horizen.utils.ByteArrayWrapper
import scorex.core.VersionTag
import scorex.util.{bytesToId, idToBytes}


import scala.collection.immutable
import scala.collection.mutable
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._


// 2 stores: one for Boxes(WalletBoxes), another for secrets
// TO DO: we need to wrap LSMStore

// TO DO: put also SidechainSecretsCompanion and SidechainBoxesCompanion with a data provided by Sidechain developer

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

class SidechainWallet(seed: Array[Byte], walletBoxStorage: SidechainWalletBoxStorage, secretStorage: SidechainSecretStorage)
  extends Wallet[Secret,
                 SidechainTypes#P,
                 SidechainTypes#BT,
                 SidechainBlock,
                 SidechainWallet]
  with NodeWallet
{
  override type NVCT = SidechainWallet

  private val _secrets = new mutable.LinkedHashMap[SidechainTypes#P, Secret]()
  private val _walletBoxes = new mutable.LinkedHashMap[Array[Byte], WalletBox]()

  private var _applicationWallet : ApplicationWallet = null

  loadSecrets
  loadWalletBoxes

  private def loadSecrets : Unit = {
    _secrets.clear()
    for (s <- secretStorage.getAll.filter(_.isSuccess)) {
      val secret = s.get
      _secrets.put(secret.publicImage().asInstanceOf[SidechainTypes#P], secret)
    }
  }

  private def loadWalletBoxes : Unit = {
    _walletBoxes.clear()
    for (wb <- walletBoxStorage.getAll.filter(_.isSuccess)) {
      _walletBoxes.put(wb.get.box.id(), wb.get)
    }
  }

  def registerApplicationWallet(applicationWallet : ApplicationWallet) : Unit = {
    _applicationWallet = applicationWallet
  }

  def unregisterApplicationWallet : Unit = {
    _applicationWallet = null
  }

  // 1) check for existence
  // 2) try to store in SecretStoreusing SidechainSecretsCompanion
  override def addSecret(secret: Secret): Try[SidechainWallet] = {
    val proposition = secret.publicImage().asInstanceOf[SidechainTypes#P]
    System.out.println(proposition)
    if (!_secrets.contains(proposition)) {
      _secrets.put(proposition, secret)
      secretStorage.update(secret)
      if (_applicationWallet != null)
        _applicationWallet.onAddSecret(secret)
      Success(this)
    } else
      Failure(new IllegalArgumentException("Key already exists."))
  }

  // 1) check for existence
  // 2) remove from SecretStore (note: provide a unique version to SecretStore)
  override def removeSecret(publicImage: SidechainTypes#P): Try[SidechainWallet] = {
    if (_secrets.contains(publicImage)) {
      val secret = _secrets.get(publicImage)
      _secrets.remove(publicImage)
      secretStorage.remove(secret.get)
      if (_applicationWallet != null)
        _applicationWallet.onRemoveSecret(secret.get)
      Success(this)
    } else
      Failure(new IllegalArgumentException("Key does not exist."))
  }

  override def secret(publicImage: SidechainTypes#P): Option[Secret] = {
    _secrets.get(publicImage)
  }

  // get all secrets, use SidechainSecretsCompanion to deserialize
  override def secrets(): immutable.Set[Secret] = {
    _secrets.values.toSet
  }

  // get all boxes as WalletBox object using SidechainBoxesCompanion
  override def boxes(): immutable.Seq[WalletBox] = {
    _walletBoxes.values.toSeq.to[immutable.Seq]
  }

  // get all secrets using SidechainSecretsCompanion -> get .publicImage of each
  override def publicKeys(): immutable.Set[SidechainTypes#P] = {
    _secrets.keys.toSet
  }

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(tx: SidechainTypes#BT): SidechainWallet = this

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(txs: Seq[SidechainTypes#BT]): SidechainWallet = this

  // scan like in HybridApp, but in more general way.
  // update boxes in BoxStore
  override def scanPersistent(modifier: SidechainBlock): SidechainWallet = {
    val changes = SidechainState.changes(modifier).get

    val newBoxes = changes.toAppend.filter(s => _secrets.contains(s.box.proposition().asInstanceOf[SidechainTypes#P]))
        .map(_.box)
        .map { box =>
               val boxTransaction = modifier.transactions.find(t => t.newBoxes().asScala.exists(tb => java.util.Arrays.equals(tb.id, box.id)))
               val txId : Array[Byte]= boxTransaction.map(_.id).get.getBytes
               val ts = boxTransaction.map(_.timestamp).getOrElse(modifier.timestamp)
               WalletBox(box.asInstanceOf[SidechainTypes#B], txId, ts)
    }

    val boxIdsToRemove = changes.toRemove.map(_.boxId).map(new ByteArrayWrapper(_))
    boxIdsToRemove.foreach((id : ByteArrayWrapper) => _walletBoxes.remove(id.data))
    newBoxes.foreach((wb : WalletBox) => _walletBoxes.put(wb.box.id(), wb))
    walletBoxStorage.update(new ByteArrayWrapper(modifier.id.getBytes), newBoxes.toList, boxIdsToRemove.toList)

    if (_applicationWallet != null) {
      _applicationWallet.onRemoveBox(boxIdsToRemove.toList.asJava)
      _applicationWallet.onNewBox(newBoxes.map(_.box.asInstanceOf[Box[_ <: Proposition]]).toList.asJava)
    }

    this
  }

  // rollback BoxStore only. SecretStore must not changed
  override def rollback(to: VersionTag): Try[SidechainWallet] = {
    val version = new ByteArrayWrapper(to.getBytes)
    if (!walletBoxStorage.lastVesrionId.orElse(version).equals(version)) {
      walletBoxStorage.rollback(new ByteArrayWrapper(to.getBytes))
      loadWalletBoxes
    }
    Success(this)
  }

  // Java NodeWallet interface definition
  override def allBoxes : JList[Box[_ <: Proposition]] = {
    _walletBoxes.values.map(_.box.asInstanceOf[Box[_ <: Proposition]]).toList.asJava
  }

  override def allBoxes(boxIdsToExclude: JList[Array[Byte]]): JList[Box[_ <: Proposition]] = {
    _walletBoxes.values.filter((wb : WalletBox) => !boxIdsToExclude.contains(wb.box.id()))
      .map(_.box.asInstanceOf[Box[_ <: Proposition]])
      .toList.asJava
  }

  override def boxesOfType(boxType: Class[_ <: Box[_ <: Proposition]]): JList[Box[_ <: Proposition]] = {
    _walletBoxes.values.filter(_.box.getClass.equals(boxType))
      .map(_.box.asInstanceOf[Box[_ <: Proposition]])
      .toList.asJava
  }

  override def boxesOfType(boxType: Class[_ <: Box[_ <: Proposition]], boxIdsToExclude: JList[Array[Byte]]): JList[Box[_ <: Proposition]] = {
    _walletBoxes.values.filter((wb : WalletBox) => !boxIdsToExclude.contains(wb.box.id()))
      .filter(_.box.getClass.equals(boxType))
      .map(_.box.asInstanceOf[Box[_ <: Proposition]])
      .toList.asJava
  }

  override def secretByPublicImage(publicImage: ProofOfKnowledgeProposition[_ <: Secret]): Secret = {
    _secrets.get(publicImage.asInstanceOf[SidechainTypes#P]).get
  }

  override def allSecrets(): JList[Secret] = {
    _secrets.values.toList.asJava
  }

  override def secretsOfType(secretType: Class[_ <: Secret]): JList[Secret] = {
    _secrets.values.filter(_.getClass.equals(secretType)).toList.asJava
  }

}
