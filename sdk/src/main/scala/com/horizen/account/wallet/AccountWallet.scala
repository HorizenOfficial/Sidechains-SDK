package com.horizen.account.wallet

import com.horizen.block.SidechainAccountBlock
import com.horizen.{SidechainTypes, Wallet}
import scorex.core.VersionTag

import scala.util.Try

class AccountWallet extends Wallet[SidechainTypes#SCS, SidechainTypes#SCP, SidechainTypes#SCAT, SidechainAccountBlock, AccountWallet] {
  override type NVCT = this.type

  override def addSecret(secret: SidechainTypes#SCS): Try[AccountWallet] = ???

  override def removeSecret(publicImage: SidechainTypes#SCP): Try[AccountWallet] = ???

  override def secret(publicImage: SidechainTypes#SCP): Option[SidechainTypes#SCS] = ???

  override def secrets(): Set[SidechainTypes#SCS] = ???

  override def publicKeys(): Set[SidechainTypes#SCP] = ???

  override def scanOffchain(tx: SidechainTypes#SCAT): AccountWallet = ???

  override def scanOffchain(txs: Seq[SidechainTypes#SCAT]): AccountWallet = ???

  override def scanPersistent(modifier: SidechainAccountBlock): AccountWallet = ???

  override def rollback(to: VersionTag): Try[AccountWallet] = ???
}
