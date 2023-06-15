package io.horizen.utxo.companion

import io.horizen.SidechainTypes
import io.horizen.cryptolibprovider.CircuitTypes
import io.horizen.cryptolibprovider.CircuitTypes.CircuitTypes
import io.horizen.transaction.{MC2SCAggregatedTransactionSerializer, TransactionSerializer}
import io.horizen.utils.{CheckedCompanion, DynamicTypedSerializer}
import io.horizen.utxo.transaction.CoreTransactionsIdsEnum._
import io.horizen.utxo.transaction._

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

case class SidechainTransactionsCompanion(customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]], circuitType: CircuitTypes = CircuitTypes.NaiveThresholdSignatureCircuit)
  extends DynamicTypedSerializer[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]](
    {
      val hashMap = new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]() {{
        put(MC2SCAggregatedTransactionId.id(), MC2SCAggregatedTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
        put(SidechainCoreTransactionId.id(), SidechainCoreTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
        put(OpenStakeTransactionId.id(), OpenStakeTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      }}
      if (circuitType == CircuitTypes.NaiveThresholdSignatureCircuitWithKeyRotation)
        hashMap.put(KeyRotationTransactionId.id(), CertificateKeyRotationTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      hashMap
    },
    customTransactionSerializers
  ) with CheckedCompanion[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]]

