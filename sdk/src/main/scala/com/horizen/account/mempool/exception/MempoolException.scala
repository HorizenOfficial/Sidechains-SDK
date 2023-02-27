package com.horizen.account.mempool.exception
import com.horizen.account.state.InvalidMessageException
import com.horizen.evm.utils.Address
import sparkz.util.ModifierId

import java.math.BigInteger

class MempoolException(message: String) extends Exception(message)

/** TxOversizedException is thrown if the transaction size exceeds maximum size (128 KB). */
case class TxOversizedException(address: Address, txSize: Long)
  extends InvalidMessageException(s"transaction size exceeds maximum size: address $address, size $txSize")

/** NonceGapTooWideException is thrown if the transaction nonce is too bog respect the state nonce (maxNonceGap). */
case class NonceGapTooWideException(txId: ModifierId, txNonce: BigInteger, stateNonce: BigInteger)
  extends MempoolException(s"nonce gap too wide: txId $txId, tx $txNonce, state $stateNonce")

case class AccountMemPoolOutOfBoundException(txId: ModifierId)
  extends MempoolException(s"adding transaction with txId $txId exceeds account available space")

case class TransactionReplaceUnderpricedException(txId: ModifierId)
  extends MempoolException(s"transaction with txId $txId cannot replace existing transaction because underpriced")

case class MemPoolOutOfBoundException(txId: ModifierId)
  extends MempoolException(s"adding transaction with txId $txId exceeds memory pool available space")
