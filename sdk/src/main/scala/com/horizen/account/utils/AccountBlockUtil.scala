package com.horizen.account.utils

import com.horizen.SidechainTypes
import com.horizen.account.transaction.EthereumTransaction

object AccountBlockUtil {
  def ethereumTransactions(sidechainTransactions: Seq[SidechainTypes#SCAT]): Seq[EthereumTransaction] = sidechainTransactions.map(_.asInstanceOf[EthereumTransaction])
}
