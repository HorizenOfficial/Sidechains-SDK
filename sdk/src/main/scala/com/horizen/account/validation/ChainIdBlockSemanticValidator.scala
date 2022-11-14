package com.horizen.account.validation

import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.{AccountTransactionsIdsEnum, EthereumTransactionNew}
import com.horizen.params.NetworkParams
import com.horizen.validation.SemanticBlockValidator

import scala.util.Try

case class ChainIdBlockSemanticValidator(params: NetworkParams) extends SemanticBlockValidator[AccountBlock] {
  override def validate(block: AccountBlock): Try[Unit] = Try {
    for (actx <- block.transactions) {
      if (actx.transactionTypeId == AccountTransactionsIdsEnum.EthereumTransactionId.id()) {
        val tx = actx.asInstanceOf[EthereumTransactionNew];
        if (tx.isSigned) {
          if (tx.isEIP1559)
            if (tx.getChainId != params.chainId)
              throw new InvalidTransactionChainIdException(s"Transaction ${tx.id} chain ID ${tx.getChainId} " +
                s"does not match network chain ID ${params.chainId}."
            )
        } else
          throw new MissingTransactionSignatureException(s"Transaction ${tx.id} without signature found in block ${block.id}");
      }
    }
  }
}
