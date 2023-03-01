package io.horizen.block

object SidechainCreationVersions extends Enumeration {
  type SidechainCreationVersion = Value

  val SidechainCreationVersion0: SidechainCreationVersion = Value(0)
  val SidechainCreationVersion1: SidechainCreationVersion = Value(1)
  val SidechainCreationVersion2: SidechainCreationVersion = Value(2)
  def SidechainCreationVersion3Plus(version: Int): SidechainCreationVersion = Value(version)

  def getVersion(version: Int): SidechainCreationVersion = {
    version match {
      case 0 => SidechainCreationVersion0
      case 1 => SidechainCreationVersion1
      case 2 => SidechainCreationVersion2
      case v if v >=3 => SidechainCreationVersion3Plus(version)
      case unknownVersion => throw new IllegalArgumentException(s"Unknown sidechain creation version $unknownVersion.")
    }
  }
}
