package com.horizen.account.utils

import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.EthereumTransaction

import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter

object AccountBlockUtil {
  def ethereumTransactions(block: AccountBlock): util.List[EthereumTransaction] =
    block.sidechainTransactions.map(_.asInstanceOf[EthereumTransaction]).asJava
}
