package com.horizen.block

object SidechainCreationVersions extends Enumeration {
  type SidechainCreationVersion = Value

  val SidechainCreationVersion0: SidechainCreationVersion = Value(0)
  val SidechainCreationVersion1: SidechainCreationVersion = Value(1)

  def getVersion(version: Int): SidechainCreationVersion = {
    version match {
      case 0 => SidechainCreationVersion0
      case 1 => SidechainCreationVersion1
      case unknownVersion => throw new IllegalArgumentException(s"Unknown sidechain creation version $unknownVersion.")
    }
  }
}
