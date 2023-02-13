package com.horizen.account.mempool.exception
import sparkz.util.ModifierId

import java.math.BigInteger

class MempoolException(message: String) extends Exception(message)

case class NonceGapTooWideException(txId: ModifierId, txNonce: BigInteger, stateNonce: BigInteger)
  extends MempoolException(s"nonce gap too wide: txId $txId, tx $txNonce, state $stateNonce")
