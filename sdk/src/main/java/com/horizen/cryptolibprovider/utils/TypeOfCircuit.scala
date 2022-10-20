package com.horizen.cryptolibprovider.utils

object TypeOfCircuit extends Enumeration {
  type TypeOfCircuit = Value

  val NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation = Value
}
