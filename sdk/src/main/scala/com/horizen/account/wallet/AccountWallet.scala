package com.horizen.account.wallet

import com.horizen.account.block.AccountBlock
import com.horizen.consensus.ConsensusEpochInfo
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainSecretStorage
import com.horizen.{SidechainTypes, Wallet}
import scorex.core.VersionTag

import scala.util.Try

class AccountWallet extends Wallet[SidechainTypes#SCS, SidechainTypes#SCP, SidechainTypes#SCAT, AccountBlock, AccountWallet] {
  override type NVCT = this.type

  override def addSecret(secret: SidechainTypes#SCS): Try[AccountWallet] = ???

  override def removeSecret(publicImage: SidechainTypes#SCP): Try[AccountWallet] = ???

  override def secret(publicImage: SidechainTypes#SCP): Option[SidechainTypes#SCS] = ???

  override def secrets(): Set[SidechainTypes#SCS] = ???

  override def publicKeys(): Set[SidechainTypes#SCP] = ???

  override def scanOffchain(tx: SidechainTypes#SCAT): AccountWallet = ???

  override def scanOffchain(txs: Seq[SidechainTypes#SCAT]): AccountWallet = ???

  override def scanPersistent(modifier: AccountBlock): AccountWallet = ???

  override def rollback(to: VersionTag): Try[AccountWallet] = ???
}


object AccountWallet
{
  private[horizen] def restoreWallet(seed: Array[Byte],
                                     secretStorage: SidechainSecretStorage,
                                     params: NetworkParams): Option[AccountWallet] = {

    //TODO: uncomment
    // Some(new AccountWallet(seed, secretStorage, params))
    Some(new AccountWallet())
  }

  private[horizen] def createGenesisWallet(seed: Array[Byte],
                                           secretStorage: SidechainSecretStorage,
                                           params: NetworkParams): Try[AccountWallet] = Try {
    //TODO: uncomment
    // new AccountWallet(seed, secretStorage, params)
    new AccountWallet()
  }
}