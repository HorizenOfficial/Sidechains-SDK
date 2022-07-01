package com.horizen

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.storage._
import com.horizen.transaction.Transaction
import scorex.util.ScorexLogging

import java.util.{List => JList, Optional => JOptional}
import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.Try

trait Wallet[S <: Secret, P <: Proposition, TX <: Transaction, PMOD <: scorex.core.PersistentNodeViewModifier, W <: Wallet[S, P, TX, PMOD, W]]
  extends scorex.core.transaction.wallet.Vault[TX, PMOD, W] {
  self: W =>

  def addSecret(secret: S): Try[W]

  def removeSecret(publicImage: P): Try[W]

  def secret(publicImage: P): Option[S]

  def secrets(): Set[S]

  def publicKeys(): Set[P]
}


abstract class AbstractWallet[
  TX <: Transaction,
  PM <: SidechainBlockBase[TX, _ <: SidechainBlockHeaderBase],
  W <: AbstractWallet[TX, PM, W]] private[horizen]
(
  seed: Array[Byte],
  secretStorage: SidechainSecretStorage
)
  extends Wallet[SidechainTypes#SCS,
    SidechainTypes#SCP,
    TX,
    PM,
    W]
    with ScorexLogging
    with NodeWalletBase
    with SidechainTypes {

  self: W =>

  // 1) check for existence
  // 2) try to store in SecretStore using SidechainSecretsCompanion
  override def addSecret(secret: SidechainTypes#SCS): Try[W] = Try{
    require(secret != null, "Secret must be NOT NULL.")
    secretStorage.add(secret).get
    this
  }

  // 1) check for existence
  // 2) remove from SecretStore (note: provide a unique version to SecretStore)
  override def removeSecret(publicImage: SidechainTypes#SCP): Try[W] = Try {
    require(publicImage != null, "PublicImage must be NOT NULL.")
    secretStorage.remove(publicImage).get
    this
  }

  override def secret(publicImage: SidechainTypes#SCP): Option[SidechainTypes#SCS] = {
    secretStorage.get(publicImage)
  }

  override def secrets(): Set[SidechainTypes#SCS] = {
    secretStorage.getAll.toSet
  }

  override def publicKeys(): Set[SidechainTypes#SCP] = {
    secretStorage.getAll.map(_.publicImage()).toSet
  }

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(tx: TX): W = this

  // just do nothing, we don't need to care about offchain objects inside the wallet
  override def scanOffchain(txs: Seq[TX]): W = this

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

  override def walletSeed(): Array[Byte] = seed

}

