package com.horizen.fixtures

import com.horizen.cryptolibprovider.utils.CumulativeHashFunctions
import com.horizen.fixtures.SidechainBlockFixture.generateBytes
import com.horizen.utils.BytesUtils

object FieldElementFixture {
  def generateFieldElement(): Array[Byte] = {
    val fieldElement: Array[Byte] = generateBytes(CumulativeHashFunctions.hashLength())
    fieldElement(fieldElement.length - 2) = 0
    fieldElement(fieldElement.length - 1) = 0
    fieldElement
  }

  def generateFieldElementBigEndian(): Array[Byte] = {
    BytesUtils.reverseBytes(generateFieldElement())
  }
}
