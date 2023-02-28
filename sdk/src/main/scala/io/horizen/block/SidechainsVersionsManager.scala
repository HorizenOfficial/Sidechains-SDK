package io.horizen.block

import io.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import io.horizen.utils.ByteArrayWrapper

trait SidechainsVersionsManager {
  def getVersion(sidechainId: ByteArrayWrapper): SidechainCreationVersion

  def getVersions(sidechainIds: Seq[ByteArrayWrapper]): Map[ByteArrayWrapper, SidechainCreationVersion]
}
