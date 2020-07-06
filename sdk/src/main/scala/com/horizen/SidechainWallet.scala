package com.horizen

import java.lang
import java.util.{List => JList, Optional => JOptional}

import com.horizen.block.SidechainBlock
import com.horizen.box.{Box, ForgerBox}
import com.horizen.consensus.{ConsensusEpochInfo, ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.wallet.ApplicationWallet
import com.horizen.node.NodeWallet
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.storage._
import com.horizen.transaction.Transaction
import com.horizen.transaction.mainchain.SidechainCreation
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ForgingStakeMerklePathInfo}
import scorex.core.VersionTag
import com.horizen.utils._
import scorex.util.ModifierId

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


class SidechainWallet private[horizen] (seed: Array[Byte],
                                        walletBoxStorage: SidechainWalletBoxStorage,
                                        secretStorage: SidechainSecretStorage,
                                        walletTransactionStorage: SidechainWalletTransactionStorage,
                                        forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
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
    val boxesInWallet = boxes().map(_.box.id())

    val txBoxes: Map[ByteArrayWrapper, SidechainTypes#SCBT] = modifier.transactions
      .foldLeft(Map.empty[ByteArrayWrapper, SidechainTypes#SCBT]) {
        (accMap, tx) => accMap ++ tx.boxIdsToOpen().asScala.map(boxId => boxId -> tx) ++
          tx.newBoxes().asScala.map(b => new ByteArrayWrapper(b.id()) -> tx)
      }

    val newBoxes = changes.toAppend.map(_.box)

    val newWalletBoxes = newBoxes.withFilter(box => pubKeys.contains(box.proposition())).map( box => {
      val boxTransaction = txBoxes(box.id())
      new WalletBox(box, ModifierId @@ boxTransaction.id, boxTransaction.timestamp())
    })

    val newDelegatedForgerBoxes: Seq[ForgerBox] = newBoxes.withFilter(_.isInstanceOf[ForgerBox]).map(_.asInstanceOf[ForgerBox])
      .filter(forgerBox => pubKeys.contains(forgerBox.blockSignProposition()))

    val boxIdsToRemove = changes.toRemove.map(_.boxId.array)
      .filter(boxId => boxesInWallet.exists(b => java.util.Arrays.equals(boxId, b)))

    val transactions = (for (boxId <- (newWalletBoxes.map(_.box.id()) ++ boxIdsToRemove))
      yield txBoxes(new ByteArrayWrapper(boxId))).distinct

    walletBoxStorage.update(new ByteArrayWrapper(version), newWalletBoxes.toList, boxIdsToRemove.toList).get

    walletTransactionStorage.update(new ByteArrayWrapper(version), transactions).get

    // We keep forger boxes separate to manage forging stake delegation
    forgingBoxesInfoStorage.updateForgerBoxes(new ByteArrayWrapper(version), newDelegatedForgerBoxes, boxIdsToRemove).get

    applicationWallet.onChangeBoxes(version, newBoxes.toList.asJava, boxIdsToRemove.toList.asJava)

    this
  }

  // rollback BoxStorage and TransactionsStorage only. SecretStorage must not change.
  override def rollback(to: VersionTag): Try[SidechainWallet] = Try {
    require(to != null, "Version to rollback to must be NOT NULL.")
    val version = new ByteArrayWrapper(BytesUtils.fromHexString(to))
    walletBoxStorage.rollback(version).get
    walletTransactionStorage.rollback(version).get
    forgingBoxesInfoStorage.rollback(version).get
    applicationWallet.onRollback(version.data)
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

  def applyConsensusEpochInfo(epochInfo: ConsensusEpochInfo): SidechainWallet = {
    val merkleTreeLeaves = epochInfo.forgingStakeInfoTree.leaves().asScala.map(leaf => new ByteArrayWrapper(leaf))

    // Calculate merkle path for all delegated forgerBoxes
    val forgingStakeMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo] = forgingBoxesInfoStorage.getForgerBoxes.getOrElse(Seq())
      .view
      .groupBy(box => (box.blockSignProposition(), box.vrfPubKey()))
      .map{ case ((blockSignKey, vrfKey), forgerBoxes) => ForgingStakeInfo(blockSignKey, vrfKey, forgerBoxes.map(_.value()).sum) }
      .flatMap(forgingStakeInfo => {
        merkleTreeLeaves.indexOf(new ByteArrayWrapper(forgingStakeInfo.hash)) match {
          case -1 => None // May occur in case if Wallet doesn't contain information about all boxes for given blockSignKey and vrfKey
          case index => Some(ForgingStakeMerklePathInfo(
            forgingStakeInfo,
            epochInfo.forgingStakeInfoTree.getMerklePathForLeaf(index)
          ))
        }
      }).toSeq

    forgingBoxesInfoStorage.updateForgingStakeMerklePathInfo(epochInfo.epoch, forgingStakeMerklePathInfoSeq).get
    this
  }

  def getForgingStakeMerklePathInfoOpt(requestedEpoch: ConsensusEpochNumber): Option[Seq[ForgingStakeMerklePathInfo]] = {
    // For given epoch N we should get data from the ending of the epoch N-2.
    // genesis block is the single and the last block of epoch 1 - that is a special case:
    // Data from epoch 1 is also valid for epoch 2, so for epoch N==2, we should get info from epoch 1.
    val storedConsensusEpochNumber: ConsensusEpochNumber = requestedEpoch match {
      case epoch if (epoch <= 2) => ConsensusEpochNumber @@ 1
      case epoch => ConsensusEpochNumber @@ (epoch - 2)
    }

    forgingBoxesInfoStorage.getForgingStakeMerklePathInfoForEpoch(storedConsensusEpochNumber)
  }

}

object SidechainWallet
{
  private[horizen] def restoreWallet(seed: Array[Byte],
                                     walletBoxStorage: SidechainWalletBoxStorage,
                                     secretStorage: SidechainSecretStorage,
                                     walletTransactionStorage: SidechainWalletTransactionStorage,
                                     forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
                                     applicationWallet: ApplicationWallet) : Option[SidechainWallet] = {

    if (!walletBoxStorage.isEmpty)
      Some(new SidechainWallet(seed, walletBoxStorage, secretStorage, walletTransactionStorage, forgingBoxesInfoStorage, applicationWallet))
    else
      None
  }

  private[horizen] def createGenesisWallet(seed: Array[Byte],
                                           walletBoxStorage: SidechainWalletBoxStorage,
                                           secretStorage: SidechainSecretStorage,
                                           walletTransactionStorage: SidechainWalletTransactionStorage,
                                           forgingBoxesInfoStorage: ForgingBoxesInfoStorage,
                                           applicationWallet: ApplicationWallet,
                                           genesisBlock: SidechainBlock,
                                           consensusEpochInfo: ConsensusEpochInfo
                                    ) : Try[SidechainWallet] = Try {

    if (walletBoxStorage.isEmpty) {
      val genesisWallet = new SidechainWallet(seed, walletBoxStorage, secretStorage, walletTransactionStorage, forgingBoxesInfoStorage, applicationWallet)
      genesisWallet.scanPersistent(genesisBlock).applyConsensusEpochInfo(consensusEpochInfo)
    }
    else
      throw new RuntimeException("WalletBox storage is not empty!")
  }
}
