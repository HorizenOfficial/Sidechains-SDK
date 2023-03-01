package io.horizen.cryptolibprovider

import io.horizen.cryptolibprovider.implementations.{CswCircuitImplZendoo, SchnorrFunctionsImplZendoo, ThresholdSignatureCircuitImplZendoo, ThresholdSignatureCircuitWithKeyRotationImplZendoo, VrfFunctionsImplZendoo}

object CryptoLibProvider {
  val vrfFunctions: VrfFunctions = new VrfFunctionsImplZendoo()
  val schnorrFunctions: SchnorrFunctions = new SchnorrFunctionsImplZendoo()
  val sigProofThresholdCircuitFunctions: ThresholdSignatureCircuit = new ThresholdSignatureCircuitImplZendoo()
  val thresholdSignatureCircuitWithKeyRotation: ThresholdSignatureCircuitWithKeyRotation =
    new ThresholdSignatureCircuitWithKeyRotationImplZendoo()
  val cswCircuitFunctions: CswCircuit = new CswCircuitImplZendoo()
  val commonCircuitFunctions: CommonCircuit = new CommonCircuit()
}
