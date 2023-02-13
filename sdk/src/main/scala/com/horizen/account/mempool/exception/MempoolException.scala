package com.horizen.account.mempool.exception
import sparkz.util.ModifierId

import java.math.BigInteger

class MempoolException(message: String) extends Exception(message)

case class NonceGapTooWideException(txId: ModifierId, txNonce: BigInteger, stateNonce: BigInteger)
  extends MempoolException(s"nonce gap too wide: txId $txId, tx $txNonce, state $stateNonce")

case class AccountMemPoolOutOfBoundException(txId: ModifierId)
  extends MempoolException(s"adding transaction with txId $txId exceeds account available space")

case class TransactionReplaceUnderpricedException(txId: ModifierId)
  extends MempoolException(s"transaction with txId $txId cannot replace existing transaction because underpriced")
