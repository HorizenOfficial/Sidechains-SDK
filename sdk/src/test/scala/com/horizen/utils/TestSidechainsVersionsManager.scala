package com.horizen.utils

import com.horizen.block.SidechainCreationVersions.{SidechainCreationVersion, SidechainCreationVersion0, SidechainCreationVersion1}
import com.horizen.block.SidechainsVersionsManager
import com.horizen.params.NetworkParams

sealed trait TestSidechainsVersionsStrategy {
  def getVersion(sidechainId: Array[Byte]): SidechainCreationVersion
}

// For every sidechain id return version 0
case object SidechainVersionZero extends TestSidechainsVersionsStrategy {
  override def getVersion(sidechainId: Array[Byte]): SidechainCreationVersion = SidechainCreationVersion0
}

// For every sidechain id return version 1
case object SidechainVersionOne extends TestSidechainsVersionsStrategy {
  override def getVersion(sidechainId: Array[Byte]): SidechainCreationVersion = SidechainCreationVersion1
}

// For every sidechain id return exception
case object UndefinedSidechainVersion extends TestSidechainsVersionsStrategy {
  override def getVersion(sidechainId: Array[Byte]): SidechainCreationVersion = {
    throw new IllegalArgumentException(s"UndefinedSidechainVersion: can't get sidechain creation version " +
      s"for sidechain id ${BytesUtils.toHexString(sidechainId)}")
  }
}

// Return only current sidechain version from given params, exception for others
case class CurrentSidechainVersionOnly(params: NetworkParams) extends TestSidechainsVersionsStrategy {
  override def getVersion(sidechainId: Array[Byte]): SidechainCreationVersion = {
    if(params.sidechainId.sameElements(sidechainId)) {
      params.sidechainCreationVersion
    } else {
      throw new IllegalArgumentException(s"CurrentSidechainVersionOnly: can't get sidechain creation version for " +
        s"unknown sidechain id ${BytesUtils.toHexString(sidechainId)}")
    }
  }
}

// Define the list of known sidechains and their versions, exception for others
case class CustomSidechainsVersions(versions: Map[ByteArrayWrapper, SidechainCreationVersion]) extends TestSidechainsVersionsStrategy {
  override def getVersion(sidechainId: Array[Byte]): SidechainCreationVersion = {
    val scId: ByteArrayWrapper = new ByteArrayWrapper(sidechainId)
    versions.getOrElse(scId, {
      throw new IllegalArgumentException(s"CustomSidechainsVersions: can't get sidechain creation version for " +
        s"unknown sidechain id ${BytesUtils.toHexString(sidechainId)}")
    })
  }
}


case class TestSidechainsVersionsManager(strategy: TestSidechainsVersionsStrategy = UndefinedSidechainVersion) extends SidechainsVersionsManager {
  override def getVersion(sidechainId: ByteArrayWrapper): SidechainCreationVersion = {
    strategy.getVersion(sidechainId)
  }

  override def getVersions(sidechainIds: Seq[ByteArrayWrapper]): Map[ByteArrayWrapper, SidechainCreationVersion] = {
    sidechainIds.map(id => id -> getVersion(id)).toMap
  }
}

object TestSidechainsVersionsManager {
  def apply(params: NetworkParams): TestSidechainsVersionsManager = {
    TestSidechainsVersionsManager(CurrentSidechainVersionOnly(params))
  }

  def apply(versions: Map[ByteArrayWrapper, SidechainCreationVersion]): TestSidechainsVersionsManager = {
    TestSidechainsVersionsManager(CustomSidechainsVersions(versions))
  }
}