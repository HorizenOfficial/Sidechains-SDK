package io.horizen.utxo.companion

import io.horizen.utxo.transaction._
import io.horizen.utxo.transaction.CoreTransactionsIdsEnum._

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}
import io.horizen.SidechainTypes
import io.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import io.horizen.transaction.{MC2SCAggregatedTransactionSerializer, TransactionSerializer}
import io.horizen.utils.{BytesUtils, DynamicTypedSerializer}
import sparkz.util.serialization.VLQByteBufferReader

import java.nio.ByteBuffer


case class SidechainTransactionsCompanion(customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]], circuitType: CircuitTypes  = CircuitTypes.NaiveThresholdSignatureCircuit)
  extends DynamicTypedSerializer[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]]( {
      val hashMap = new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]() {{
        put(MC2SCAggregatedTransactionId.id(), MC2SCAggregatedTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
        put(SidechainCoreTransactionId.id(), SidechainCoreTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
        put(OpenStakeTransactionId.id(), OpenStakeTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      }}
      if (circuitType == CircuitTypes.NaiveThresholdSignatureCircuitWithKeyRotation)
        hashMap.put(KeyRotationTransactionId.id(), CertificateKeyRotationTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      hashMap
    },
    customTransactionSerializers)
{
  override def parseBytes(bytes: Array[Byte]): SidechainTypes#SCBT = {
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
