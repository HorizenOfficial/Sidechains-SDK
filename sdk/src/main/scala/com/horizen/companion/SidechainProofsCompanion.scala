package com.horizen.companion

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.horizen.SidechainTypes
import com.horizen.proof.CoreProofsIdsEnum.{Signature25519Id, VrfProofId, SchnorrSignatureId}
import com.horizen.proof._
import com.horizen.utils.DynamicTypedSerializer


case class SidechainProofsCompanion(customSerializers: JHashMap[JByte, ProofSerializer[SidechainTypes#SCPR]])
  extends DynamicTypedSerializer[SidechainTypes#SCPR, ProofSerializer[SidechainTypes#SCPR]](
    new JHashMap[JByte, ProofSerializer[SidechainTypes#SCPR]]() {{
      put(Signature25519Id.id(), Signature25519Serializer.getSerializer.asInstanceOf[ProofSerializer[SidechainTypes#SCPR]])
      put(VrfProofId.id(), VrfProofSerializer.getSerializer.asInstanceOf[ProofSerializer[SidechainTypes#SCPR]])
      put(SchnorrSignatureId.id(), SchnorrSignatureSerializer.getSerializer.asInstanceOf[ProofSerializer[SidechainTypes#SCPR]])
    }},
    customSerializers)
