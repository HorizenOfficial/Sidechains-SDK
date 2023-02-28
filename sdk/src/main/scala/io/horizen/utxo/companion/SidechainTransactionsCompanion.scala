package io.horizen.utxo.companion

import com.horizen.utxo.transaction._
import com.horizen.utxo.transaction.CoreTransactionsIdsEnum._

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}
import com.horizen.SidechainTypes
import com.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import com.horizen.transaction.{MC2SCAggregatedTransactionSerializer, TransactionSerializer}
import com.horizen.utils.DynamicTypedSerializer


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
