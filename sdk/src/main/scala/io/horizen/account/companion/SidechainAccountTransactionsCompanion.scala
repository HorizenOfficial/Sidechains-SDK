package io.horizen.account.companion

import io.horizen.SidechainTypes
import io.horizen.account.transaction.AccountTransactionsIdsEnum.EthereumTransactionId
import io.horizen.account.transaction.EthereumTransactionSerializer
import io.horizen.transaction.TransactionSerializer
import io.horizen.utils.{BytesUtils, DynamicTypedSerializer}
import sparkz.util.serialization.VLQByteBufferReader

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}
import java.nio.ByteBuffer

case class SidechainAccountTransactionsCompanion(customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]])
  extends DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]](
    new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]]() {
      {
        put(EthereumTransactionId.id(), EthereumTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])
      }
    },
    customAccountTransactionSerializers)
    {
      override def parseBytes(bytes: Array[Byte]): SidechainTypes#SCAT = {
        val reader = new VLQByteBufferReader(ByteBuffer.wrap(bytes))
        val parsedTransaction = parse(reader)
        val size = reader.remaining
        if (size > 0) {
          val trailingBytes = reader.getBytes(size)
          throw new IllegalArgumentException(
            s"Spurious bytes found in byte stream after obj parsing: [${BytesUtils.toHexString(trailingBytes)}]...")
        }
        parsedTransaction
      }
    }
