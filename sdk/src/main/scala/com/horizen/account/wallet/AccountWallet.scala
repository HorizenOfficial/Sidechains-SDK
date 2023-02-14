
package com.horizen.account.wallet

import com.horizen.account.block.AccountBlock
import com.horizen.consensus.ConsensusEpochInfo
import com.horizen.node.NodeWalletBase
import com.horizen.storage.SidechainSecretStorage
import com.horizen.{AbstractWallet, SidechainTypes, WalletReader}
import sparkz.core.VersionTag
import sparkz.util.SparkzLogging

import scala.util.Try


class AccountWallet private[horizen](seed: Array[Byte],
                                     secretStorage: SidechainSecretStorage)
  extends AbstractWallet[
    SidechainTypes#SCAT,
    AccountBlock,
    AccountWallet](seed, secretStorage)
    with SparkzLogging
    with NodeWalletBase {
  override type NVCT = this.type


  //Nothing to rollback for SecretStorage
  override def rollback(to: VersionTag): Try[AccountWallet] = Try {
    this
  }

  //AccountWallet doesn't need to store any information from the block, unlike UTXO, because
  // Account model doesn't currently support CSW, fee payments and forger stakes are stored by AccountState in the metadata
  // storage and in StateDb respectively.
  override def scanPersistent(modifier: AccountBlock): AccountWallet = {
    this
  }

  //AccountWallet doesn't store forger stakes information because they are stored by AccountState in the StateDb.
  override def applyConsensusEpochInfo(epochInfo: ConsensusEpochInfo): AccountWallet = {
    this
  }

   def getWalletReader: WalletReader = {
    new AccountWalletReader(secretStorage)
  }
}

class AccountWalletReader(secretStorage: SidechainSecretStorage) extends WalletReader {
  override def getPublicKeys: Set[Array[Byte]] = {
    secretStorage.getAll.map(secret => secret.publicImage().pubKeyBytes()).toSet
  }

  override type NVCT = this.type
}

object AccountWallet {
  private[horizen] def restoreWallet(seed: Array[Byte],
                                     secretStorage: SidechainSecretStorage): Option[AccountWallet] = {

    Some(new AccountWallet(seed, secretStorage))
  }

  private[horizen] def createGenesisWallet(seed: Array[Byte],
                                           secretStorage: SidechainSecretStorage
                                          ): Try[AccountWallet] = Try {

    new AccountWallet(seed, secretStorage)
  }
}
