package com.horizen

import java.io.{File => JFile}
import java.lang
import java.util.{List => JList, Optional => JOptional}

import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion}
import com.horizen.wallet.ApplicationWallet
import com.horizen.node.NodeWallet
import com.horizen.params.StorageParams
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator, Secret}
import com.horizen.storage.{IODBStoreAdapter, SidechainSecretStorage, SidechainWalletBoxStorage, SidechainWalletTransactionStorage, Storage}
import com.horizen.transaction.Transaction
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import io.iohk.iodb.LSMStore
import scorex.core.{VersionTag, bytesToVersion, idToVersion}
import scorex.util.ScorexLogging
import com.horizen.utils.BytesUtils

import scala.util.{Failure, Random, Success, Try}
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

class SidechainWallet private[horizen] (seed: Array[Byte], walletBoxStorage: SidechainWalletBoxStorage, secretStorage: SidechainSecretStorage,
                                        walletTransactionStorage: SidechainWalletTransactionStorage, applicationWallet: ApplicationWallet)
  extends Wallet[SidechainTypes#SCS,
                 SidechainTypes#SCP,
                 SidechainTypes#SCBT,
                 SidechainBlock,
                 SidechainWallet]
  with SidechainTypes
  with NodeWallet
{
  override type NVCT = SidechainWallet

  require(applicationWallet != null, "ApplicationWallet must be NOT NULL.")

  // 1) check for existence
  // 2) try to store in SecretStore using SidechainSecretsCompanion
  override def addSecret(secret: SidechainTypes#SCS): Try[SidechainWallet] = Try {
    require(secret != null, "Secret must be NOT NULL.")
    secretStorage.add(secret).get
    //TODO (Alberto) should we catch user exception here (and don't return it outside)???
    applicationWallet.onAddSecret(secret)
    this
  }

  // 1) check for existence
  // 2) remove from SecretStore (note: provide a unique version to SecretStore)
  override def removeSecret(publicImage: SidechainTypes#SCP): Try[SidechainWallet] = Try {
    require(publicImage != null, "PublicImage must be NOT NULL.")
    secretStorage.remove(publicImage).get
    applicationWallet.onRemoveSecret(publicImage)
    this
  }

  override def secret(publicImage: SidechainTypes#SCP): Option[SidechainTypes#SCS] = {
    secretStorage.get(publicImage)
  }

  override def secrets(): Set[SidechainTypes#SCS] = {
    secretStorage.getAll.toSet
  }

  override def boxes(): Seq[WalletBox] = {
    walletBoxStorage.getAll
  }

  override def publicKeys(): Set[SidechainTypes#SCP] = {
    secretStorage.getAll.map(_.publicImage()).toSet
  }

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(tx: SidechainTypes#SCBT): SidechainWallet = this

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(txs: Seq[SidechainTypes#SCBT]): SidechainWallet = this

  // scan like in HybridApp, but in more general way.
  // update boxes in BoxStore
  override def scanPersistent(modifier: SidechainBlock): SidechainWallet = {
    //require(modifier != null, "SidechainBlock must be NOT NULL.")
    val version = BytesUtils.fromHexString(modifier.id)
    val changes = SidechainState.changes(modifier).get
    val pubKeys = publicKeys()
    val boxesInWallet = boxes().map(_.box.id())

    val txBoxes: Seq[(SidechainTypes#SCBT, ByteArrayWrapper)] =
      (modifier.transactions.map(tx => (tx, tx.boxIdsToOpen().asScala.toSeq)) ++
        modifier.transactions.map(tx => (tx, tx.newBoxes().asScala.map(b => new ByteArrayWrapper(b.id())))))
        .flatMap(tx => {
          tx._2.foldLeft(Seq[(SidechainTypes#SCBT, ByteArrayWrapper)]())((col, box) => {
            col :+ (tx._1, box)
          })
        })

    val newBoxes = changes.toAppend.filter(s => pubKeys.contains(s.box.proposition()))
        .map(_.box)
        .map { box =>
               val boxTransaction = txBoxes.find(t => java.util.Arrays.equals(t._2.data, box.id))
               val txId = boxTransaction.map(_._1.id).get
               val ts = boxTransaction.map(_._1.timestamp).getOrElse(modifier.timestamp)
               new WalletBox(box, txId, ts)
    }

    val boxIdsToRemove = changes.toRemove.map(_.boxId.array)
      .filter(boxId => boxesInWallet.exists(b => java.util.Arrays.equals(boxId, b)))

    val transactions = txBoxes.filter(t => {
      newBoxes.exists(_.transactionId.equals(t._1.id)) ||
      boxIdsToRemove.exists(boxId => java.util.Arrays.equals(boxId, t._2.data))
    }).map(_._1).distinct

    walletBoxStorage.update(new ByteArrayWrapper(version), newBoxes.toList, boxIdsToRemove.toList).get

    walletTransactionStorage.update(new ByteArrayWrapper(version), transactions)
    applicationWallet.onChangeBoxes(version, newBoxes.map(_.box).toList.asJava,
      boxIdsToRemove.toList.asJava)

    this
  }

  // rollback BoxStore only. SecretStore must not changed
  override def rollback(to: VersionTag): Try[SidechainWallet] = Try {
    require(to != null, "Version to rollback to must be NOT NULL.")
    val version = BytesUtils.fromHexString(to)
    walletBoxStorage.rollback(new ByteArrayWrapper(version)).get
    applicationWallet.onRollback(version)
    this
  }

  // Java NodeWallet interface definition
  override def allBoxes : JList[Box[Proposition]] = {
    walletBoxStorage.getAll.map(_.box).asJava
  }

  override def allBoxes(boxIdsToExclude: JList[Array[Byte]]): JList[Box[Proposition]] = {
    walletBoxStorage.getAll
      .filter((wb : WalletBox) => !BytesUtils.contains(boxIdsToExclude, wb.box.id()))
      .map(_.box)
      .asJava
  }

  override def boxesOfType(boxType: Class[_ <: Box[_ <: Proposition]]): JList[Box[Proposition]] = {
    walletBoxStorage.getByType(boxType)
      .map(_.box)
      .asJava
  }

  override def boxesOfType(boxType: Class[_ <: Box[_ <: Proposition]], boxIdsToExclude: JList[Array[Byte]]): JList[Box[Proposition]] = {
    walletBoxStorage.getByType(boxType)
      .filter((wb : WalletBox) => !BytesUtils.contains(boxIdsToExclude, wb.box.id()))
      .map(_.box)
      .asJava
  }

  override def boxesBalance(boxType: Class[_ <: Box[_ <: Proposition]]): java.lang.Long = {
    walletBoxStorage.getBoxesBalance(boxType)
  }

  override def secretByPublicKey(publicKey: Proposition): JOptional[Secret] = {
    secretStorage.get(publicKey) match {
      case Some(secret) => JOptional.of(secret)
      case None => JOptional.empty()
    }
  }

  override def allSecrets(): JList[Secret] = {
    secretStorage.getAll.asJava
  }

  override def secretsOfType(secretType: Class[_ <: Secret]): JList[Secret] = {
    secretStorage.getAll.filter(_.getClass.equals(secretType)).asJava
  }

  override def allBoxesBalance(): lang.Long = {
    walletBoxStorage.getAll.map(_.box.value()).sum
  }

  override def walletSeed(): Array[Byte] = seed
}

object SidechainWallet
{
  private[horizen] def restoreWallet(seed: Array[Byte], walletBoxStorage: SidechainWalletBoxStorage, secretStorage: SidechainSecretStorage,
                                     walletTransactionStorage: SidechainWalletTransactionStorage, applicationWallet: ApplicationWallet) : Option[SidechainWallet] = {

    if (!walletBoxStorage.isEmpty)
      Some(new SidechainWallet(seed, walletBoxStorage, secretStorage, walletTransactionStorage, applicationWallet))
    else
      None
  }

  private[horizen] def genesisWallet(seed: Array[Byte], walletBoxStorage: SidechainWalletBoxStorage, secretStorage: SidechainSecretStorage,
                                     walletTransactionStorage: SidechainWalletTransactionStorage, applicationWallet: ApplicationWallet,
                                     genesisBlock: SidechainBlock) : Try[SidechainWallet] = Try {

    if (walletBoxStorage.isEmpty)
      new SidechainWallet(seed, walletBoxStorage, secretStorage, walletTransactionStorage, applicationWallet)
        .scanPersistent(genesisBlock)
    else
      throw new RuntimeException("WalletBox storage is not empty!")
  }
}
