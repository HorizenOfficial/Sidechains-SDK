package com.horizen.account.companion

import com.horizen.SidechainTypes
import com.horizen.account.transaction.AccountTransactionsIdsEnum.EthereumTransactionId
import com.horizen.account.transaction.EthereumTransactionSerializer
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.{BytesUtils, DynamicTypedSerializer}
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
