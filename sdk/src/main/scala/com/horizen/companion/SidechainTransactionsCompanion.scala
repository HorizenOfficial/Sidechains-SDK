package com.horizen.companion

import com.horizen.transaction._
import com.horizen.transaction.CoreTransactionsIdsEnum._

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}
import com.horizen.SidechainTypes
import com.horizen.cryptolibprovider.utils.TypeOfCircuit
import com.horizen.transaction.serializers.{KeyRotationTransactionSerializer, MC2SCAggregatedTransactionSerializer, OpenStakeTransactionSerializer, SidechainCoreTransactionSerializer}
import com.horizen.utils.DynamicTypedSerializer


case class SidechainTransactionsCompanion(customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]], typeOfCircuit: Int = TypeOfCircuit.NaiveThresholdSignatureCircuit.id)
  extends DynamicTypedSerializer[SidechainTypes#SCBT, TransactionSerializer[SidechainTypes#SCBT]]( {
      val hashMap = new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]() {{
        put(MC2SCAggregatedTransactionId.id(), MC2SCAggregatedTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
        put(SidechainCoreTransactionId.id(), SidechainCoreTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
        put(OpenStakeTransactionId.id(), OpenStakeTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      }}
      if (TypeOfCircuit(typeOfCircuit) == TypeOfCircuit.NaiveThresholdSignatureCircuitWithKeyRotation)
        hashMap.put(KeyRotationTransactionId.id(), KeyRotationTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
      hashMap
    },
    customTransactionSerializers)
