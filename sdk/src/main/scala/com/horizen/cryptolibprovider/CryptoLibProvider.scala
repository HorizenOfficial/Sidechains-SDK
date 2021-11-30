package com.horizen.cryptolibprovider

object CryptoLibProvider {
  val vrfFunctions: VrfFunctions = new VrfFunctionsImplZendoo()
  val schnorrFunctions: SchnorrFunctions = new SchnorrFunctionsImplZendoo()
  val sigProofThresholdCircuitFunctions: ThresholdSignatureCircuit = new ThresholdSignatureCircuitImplZendoo()
  val cswCircuitFunctions: CswCircuit = new CswCircuitImplZendoo()
  val commonCircuitFunctions: CommonCircuit = new CommonCircuit()
}
