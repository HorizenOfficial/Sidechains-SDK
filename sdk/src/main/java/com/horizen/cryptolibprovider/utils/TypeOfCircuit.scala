package com.horizen.cryptolibprovider.utils

object TypeOfCircuit extends Enumeration {
  type Int = Value

  val NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation = Value
}
