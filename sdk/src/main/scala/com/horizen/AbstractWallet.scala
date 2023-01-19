package com.horizen

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.consensus.ConsensusEpochInfo
import com.horizen.node.NodeWalletBase
import com.horizen.proposition.{ProofOfKnowledgeProposition, Proposition, PublicKey25519Proposition, SchnorrProposition, VrfPublicKey}
import com.horizen.secret.{PrivateKey25519, SchnorrSecret, Secret, SecretCreator, VrfSecretKey}
import com.horizen.storage._
import com.horizen.transaction.Transaction
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

import java.util.{List => JList, Optional => JOptional}
import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.util.control.Breaks.break

trait Wallet[S <: Secret, P <: Proposition, TX <: Transaction, PMOD <: sparkz.core.PersistentNodeViewModifier, W <: Wallet[S, P, TX, PMOD, W]]
  extends sparkz.core.transaction.wallet.Vault[TX, PMOD, W] {
  self: W =>

  def addSecret(secret: S): Try[W]

  def removeSecret(publicImage: P): Try[W]

  def secret(publicImage: P): Option[S]

  def secrets(): Set[S]

  def publicKeys(): Set[P]
}


abstract class AbstractWallet[
  TX <: Transaction,
  PMOD <: sparkz.core.PersistentNodeViewModifier,
  W <: AbstractWallet[TX, PMOD, W]] private[horizen]
(
  seed: Array[Byte],
  secretStorage: SidechainSecretStorage
)
  extends Wallet[SidechainTypes#SCS,
    SidechainTypes#SCP,
    TX,
    PMOD,
    W]
    with ScorexLogging
    with NodeWalletBase
    with SidechainTypes {

  self: W =>

  // 1) check for existence
  // 2) try to store in SecretStore using SidechainSecretsCompanion
  override def addSecret(secret: SidechainTypes#SCS): Try[W] = Try{
    require(secret != null, "AbstractWallet: Secret must be NOT NULL.")
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

  def applyConsensusEpochInfo(epochInfo: ConsensusEpochInfo): W

  def generateNextSecret[T <: Secret](secretCreator: SecretCreator[T]): Try[(W, T)] = Try {
    require(secretCreator != null, "AbstractWallet: Secret creator must be NOT NULL.")
    val allSecrets = this.secrets()
    var nonce = allSecrets.size
    val salt: Array[Byte] = secretCreator.salt()
    val secretStorageSize = allSecrets.count(_.isInstanceOf[T])
    for(_ <- 0 to secretStorageSize) {
      val seed = Blake2b256.hash(Bytes.concat(this.seed, Ints.toByteArray(nonce), salt))
      val secret: T = secretCreator.generateSecret(seed)
      val trySecret = secretStorage.add(secret)
      if(trySecret.isSuccess) {
        return Success(this, secret)
      } else {
        nonce += 1
      }
    }
    throw new RuntimeException("Exceeded number of attempts generating secret")
  }

  override def secretByPublicKey25519Proposition(publicKey: PublicKey25519Proposition): JOptional[PrivateKey25519] = {
    secretStorage.get(publicKey) match {
      case Some(secret) => JOptional.of(secret.asInstanceOf[PrivateKey25519])
      case None => JOptional.empty()
    }
  }


  override def secretBySchnorrProposition(publicKey: SchnorrProposition): JOptional[SchnorrSecret] = {
    secretStorage.get(publicKey) match {
      case Some(secret) => JOptional.of(secret.asInstanceOf[SchnorrSecret])
      case None => JOptional.empty()
    }
  }


  override def secretByVrfPublicKey(publicKey: VrfPublicKey): JOptional[VrfSecretKey] = {
    secretStorage.get(publicKey) match {
      case Some(secret) => JOptional.of(secret.asInstanceOf[VrfSecretKey])
      case None => JOptional.empty()
    }
  }


  override def secretsByProposition[S <: SidechainTypes#SCS](proposition: ProofOfKnowledgeProposition[S]): JList[S] = {
    proposition.canBeProvedBy(secretStorage.getAll.asJava).secretsNeeded()
  }

  override def secretByPublicKeyBytes[S <: SidechainTypes#SCS](proposition: Array[Byte]): JOptional[S] = {
    secretStorage.getAll.find(secret => java.util.Arrays.equals(secret.publicImage().pubKeyBytes(), proposition)) match {
      case Some(s) => JOptional.of(s.asInstanceOf[S])
      case None => JOptional.empty()
    }
  }
}

