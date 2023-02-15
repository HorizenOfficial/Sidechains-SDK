package com.horizen.cryptolibprovider.utils

object CircuitTypes extends Enumeration {
  type CircuitTypes = Value
  val NaiveThresholdSignatureCircuit: CircuitTypes.Value = CircuitTypes.Value(0)
  val NaiveThresholdSignatureCircuitWithKeyRotation: CircuitTypes.Value = CircuitTypes.Value(1)
}
