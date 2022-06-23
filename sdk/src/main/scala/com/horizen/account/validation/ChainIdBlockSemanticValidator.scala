package com.horizen.account.validation

import com.horizen.account.block.AccountBlock
import com.horizen.account.transaction.{AccountTransactionsIdsEnum, EthereumTransaction}
import com.horizen.params.NetworkParams
import com.horizen.transaction.Transaction
import com.horizen.validation.SemanticBlockValidator
import org.web3j.crypto.{RawTransaction, SignedRawTransaction}

import scala.util.Try

class ChainIdBlockSemanticValidator(params: NetworkParams) extends SemanticBlockValidator[AccountBlock] {
  override def validate(block: AccountBlock): Try[Unit] = Try {
    for (actx <- block.transactions) {
      if (actx.transactionTypeId == AccountTransactionsIdsEnum.EthereumTransaction.id()) {
        val tx = actx.asInstanceOf[EthereumTransaction];
        if (tx.isSigned) {
          if (tx.isEIP1559)
            if (tx.getChainId != params.chainId) throw new InvalidTransactionChainIdException(s"Tx chain ID " +
              s"${tx.getChainId} does not match actual chain ID ${params.chainId}."
            )
        } else
          throw new MissingTransactionSignatureException(s"Unsigned transaction found in block");
      }
    }
  }
}
