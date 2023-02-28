package io.horizen.fixtures

import io.horizen.cryptolibprovider.utils.CumulativeHashFunctions
import io.horizen.fixtures.SidechainBlockFixture.generateBytes
import io.horizen.utils.BytesUtils

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
