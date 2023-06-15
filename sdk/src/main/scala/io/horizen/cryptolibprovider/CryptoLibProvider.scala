package io.horizen.cryptolibprovider

import io.horizen.cryptolibprovider.implementations._

object CryptoLibProvider {
  val vrfFunctions: VrfFunctions = new VrfFunctionsImplZendoo()
  val schnorrFunctions: SchnorrFunctions = new SchnorrFunctionsImplZendoo()
  val sigProofThresholdCircuitFunctions: ThresholdSignatureCircuit = new ThresholdSignatureCircuitImplZendoo()
  val thresholdSignatureCircuitWithKeyRotation: ThresholdSignatureCircuitWithKeyRotation =
    new ThresholdSignatureCircuitWithKeyRotationImplZendoo()
  val cswCircuitFunctions: CswCircuit = new CswCircuitImplZendoo()
  val sc2scCircuitFunctions: Sc2scCircuit = new Sc2scImplZendoo()
  val commonCircuitFunctions: CommonCircuit = new CommonCircuit()
}
