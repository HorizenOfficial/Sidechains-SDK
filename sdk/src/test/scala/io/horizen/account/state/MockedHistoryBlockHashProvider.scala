package io.horizen.account.state

import sparkz.core.bytesToId
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger

object MockedHistoryBlockHashProvider extends HistoryBlockHashProvider {
  override def blockIdByHeight(height: Int): Option[String] =
    Some(bytesToId(Keccak256.hash(BigInteger.valueOf(height).toByteArray)))
}
