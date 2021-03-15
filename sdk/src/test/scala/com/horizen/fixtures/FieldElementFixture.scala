package com.horizen.fixtures

import com.horizen.cryptolibprovider.CumulativeHashFunctions
import com.horizen.fixtures.SidechainBlockFixture.generateBytes

object FieldElementFixture {
  def generateFiledElement(): Array[Byte] = {
    val fieldElement: Array[Byte] = generateBytes(CumulativeHashFunctions.hashLength())
    fieldElement(fieldElement.length - 2) = 0
    fieldElement(fieldElement.length - 1) = 0
    fieldElement
  }
}
