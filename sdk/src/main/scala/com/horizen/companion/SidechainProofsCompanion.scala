package com.horizen.companion

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes
import com.horizen.proof.CoreProofsIdsEnum.Signature25519Id
import com.horizen.proof.{ProofSerializer, Signature25519Serializer}
import com.horizen.utils.DynamicTypedSerializer


case class SidechainProofsCompanion(customSerializers: JHashMap[JByte, ProofSerializer[SidechainTypes#SCPR]])
  extends DynamicTypedSerializer[SidechainTypes#SCPR, ProofSerializer[SidechainTypes#SCPR]](
    new JHashMap[JByte, ProofSerializer[SidechainTypes#SCPR]]() {{
      put(Signature25519Id.id(), Signature25519Serializer.getSerializer.asInstanceOf[ProofSerializer[SidechainTypes#SCPR]])
    }},
    customSerializers)
