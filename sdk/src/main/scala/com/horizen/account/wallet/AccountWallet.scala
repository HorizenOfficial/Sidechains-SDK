
package com.horizen.account.wallet

import com.horizen.account.block.AccountBlock
import com.horizen.node.NodeWalletBase
import com.horizen.storage.SidechainSecretStorage
import com.horizen.{AbstractWallet, SidechainTypes}
import scorex.core.VersionTag
import scorex.util.ScorexLogging

import scala.util.Try


class AccountWallet private[horizen](seed: Array[Byte],
                                     secretStorage: SidechainSecretStorage)
  extends AbstractWallet[
    SidechainTypes#SCAT,
    AccountBlock,
    AccountWallet](seed, secretStorage)
    with ScorexLogging
    with NodeWalletBase {
  override type NVCT = this.type


  //Nothing to rollback for SecretStorage
  override def rollback(to: VersionTag): Try[AccountWallet] = Try {
    this
  }

}

object AccountWallet {
  private[horizen] def restoreWallet(seed: Array[Byte],
                                     secretStorage: SidechainSecretStorage): Option[AccountWallet] = {

    if (!secretStorage.isEmpty) {
      Some(new AccountWallet(seed, secretStorage))
    } else
      None
  }

  private[horizen] def createGenesisWallet(seed: Array[Byte],
                                           secretStorage: SidechainSecretStorage
                                          ): Try[AccountWallet] = Try {

    if (secretStorage.isEmpty) {
      new AccountWallet(seed, secretStorage)
    }
    else
      throw new RuntimeException("Secret storage is not empty!")
  }
}
