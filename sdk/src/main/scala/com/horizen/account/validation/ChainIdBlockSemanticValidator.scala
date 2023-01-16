package com.horizen.account.validation

import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.{AccountTransactionsIdsEnum, EthereumTransaction}
import com.horizen.params.NetworkParams
import com.horizen.validation.SemanticBlockValidator

import scala.util.Try

case class ChainIdBlockSemanticValidator(params: NetworkParams) extends SemanticBlockValidator[AccountBlock] {
  override def validate(block: AccountBlock): Try[Unit] = Try {
    for (tx <- block.transactions) {
      if (tx.transactionTypeId == AccountTransactionsIdsEnum.EthereumTransactionId.id()) {
        val ethTx = tx.asInstanceOf[EthereumTransaction]
        if (ethTx.isSigned) {
          if (ethTx.isEIP1559 || ethTx.isEIP155) {
            if (ethTx.getChainId != params.chainId) {
              throw new InvalidTransactionChainIdException(s"Transaction ${ethTx.id} chain ID ${ethTx.getChainId} " +
                s"does not match network chain ID ${params.chainId}")
            }
          }
        } else {
          throw new MissingTransactionSignatureException(s"Transaction ${ethTx.id} without signature found in block ${block.id}")
        }
      }
    }
  }
}
