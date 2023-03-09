package io.horizen.account.history.validation

import io.horizen.account.block.AccountBlock
import io.horizen.account.transaction.{AccountTransactionsIdsEnum, EthereumTransaction}
import io.horizen.history.validation.SemanticBlockValidator
import io.horizen.params.NetworkParams
import sparkz.util.SparkzLogging

import scala.util.Try

case class ChainIdBlockSemanticValidator(params: NetworkParams) extends SparkzLogging with SemanticBlockValidator[AccountBlock] {
  override def validate(block: AccountBlock): Try[Unit] = Try {
    for (tx <- block.transactions) {
      if (tx.transactionTypeId == AccountTransactionsIdsEnum.EthereumTransactionId.id()) {
        val ethTx = tx.asInstanceOf[EthereumTransaction]
        if (ethTx.isSigned) {
          if (ethTx.isEIP1559 || ethTx.isEIP155) {
            if (ethTx.getChainId != params.chainId) {
              val errMsg = s"Transaction ${ethTx.id} chain ID ${ethTx.getChainId} " +
                s"does not match network chain ID ${params.chainId}"
              log.warn(errMsg)
              throw new InvalidTransactionChainIdException(errMsg)
            }
          }
        } else {
          throw new MissingTransactionSignatureException(s"Transaction ${ethTx.id} without signature found in block ${block.id}")
        }
      }
    }
  }
}
