package com.horizen.block

import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import com.horizen.utils.ByteArrayWrapper

trait SidechainsVersionsManager {
  def getVersion(sidechainId: ByteArrayWrapper): SidechainCreationVersion

  def getVersions(sidechainIds: Seq[ByteArrayWrapper]): Map[ByteArrayWrapper, SidechainCreationVersion]
}
