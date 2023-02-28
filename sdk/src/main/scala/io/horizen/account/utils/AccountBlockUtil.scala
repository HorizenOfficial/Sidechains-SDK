package io.horizen.account.utils

import io.horizen.account.block.AccountBlock
import io.horizen.account.transaction.EthereumTransaction

import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter

object AccountBlockUtil {
  def ethereumTransactions(block: AccountBlock): util.List[EthereumTransaction] =
    block.sidechainTransactions.map(_.asInstanceOf[EthereumTransaction]).asJava
}
