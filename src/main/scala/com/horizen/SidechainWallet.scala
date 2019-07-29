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
import com.horizen.secret.Secret
import com.horizen.storage.{IODBStoreAdapter, SidechainSecretStorage, SidechainWalletBoxStorage, Storage}
import com.horizen.transaction.Transaction
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import io.iohk.iodb.LSMStore
import scorex.core.{VersionTag, bytesToVersion, idToVersion}
import scorex.util.ScorexLogging

import scala.util.{Failure, Success, Try}
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

class SidechainWallet private[horizen] (walletBoxStorage: SidechainWalletBoxStorage, secretStorage: SidechainSecretStorage,
                               applicationWallet: ApplicationWallet)
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

    val newBoxes = changes.toAppend.filter(s => pubKeys.contains(s.box.proposition()))
        .map(_.box)
        .map { box =>
               val boxTransaction = modifier.transactions.find(t => t.newBoxes().asScala.exists(tb => java.util.Arrays.equals(tb.id, box.id)))
               val txId = boxTransaction.map(_.id).get
               val ts = boxTransaction.map(_.timestamp).getOrElse(modifier.timestamp)
               new WalletBox(box, txId, ts)
    }

    val boxIdsToRemove = changes.toRemove.map(_.boxId.array)
    walletBoxStorage.update(new ByteArrayWrapper(version), newBoxes.toList, boxIdsToRemove.toList).get

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
}

object SidechainWallet
  extends ScorexLogging
{
  private def openStorage(storagePath: JFile) : Storage = {
    storagePath.mkdirs()
    new IODBStoreAdapter(new LSMStore(storagePath, StorageParams.storageKeySize))
  }

  private def openWalletBoxStorage(storage: Storage, sidechainBoxesCompanion: SidechainBoxesCompanion) : SidechainWalletBoxStorage = {

    val walletBoxStorage = new SidechainWalletBoxStorage(storage, sidechainBoxesCompanion)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        storage.close()
      }
    })

    walletBoxStorage
  }

  private def openSecretStorage(storage: Storage, sidechainSecretsCompanion: SidechainSecretsCompanion) : SidechainSecretStorage = {

    val secretStorage = new SidechainSecretStorage(storage, sidechainSecretsCompanion)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        storage.close()
      }
    })

    secretStorage
  }

  private[horizen] def restoreWallet(sidechainSettings: SidechainSettings, applicationWallet: ApplicationWallet,
                                     sidechainBoxesCompanion: SidechainBoxesCompanion, sidechainSecretsCompanion: SidechainSecretsCompanion,
                                     externalStorage: Option[Storage]) : Option[SidechainWallet] = {

    val walletBoxStorage = externalStorage.getOrElse(openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/wallet")))
    val secretStorage = externalStorage.getOrElse(openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/secret")))

    if (walletBoxStorage.lastVersionID().isPresent && secretStorage.lastVersionID().isPresent)
      Some(new SidechainWallet(openWalletBoxStorage(walletBoxStorage, sidechainBoxesCompanion),
        openSecretStorage(secretStorage, sidechainSecretsCompanion), applicationWallet))
    else
      None
  }

  private[horizen] def genesisWallet(sidechainSettings: SidechainSettings, applicationWallet: ApplicationWallet,
                                     sidechainBoxesCompanion: SidechainBoxesCompanion, sidechainSecretsCompanion: SidechainSecretsCompanion,
                                     externalStorage: Option[Storage]) : Option[SidechainWallet] = {

    val walletBoxStorage = externalStorage.getOrElse(openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/wallet")))
    val secretStorage = externalStorage.getOrElse(openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/secret")))

    if (!walletBoxStorage.lastVersionID().isPresent && !secretStorage.lastVersionID().isPresent)
      Some(new SidechainWallet(openWalletBoxStorage(walletBoxStorage, sidechainBoxesCompanion),
                          openSecretStorage(secretStorage, sidechainSecretsCompanion), applicationWallet)
        .scanPersistent(sidechainSettings.genesisBlock.get))
    else
      None
  }
}
