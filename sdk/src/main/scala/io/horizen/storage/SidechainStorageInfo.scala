package io.horizen.storage

import io.horizen.utils.ByteArrayWrapper

trait SidechainStorageInfo {
  def lastVersionId : Option[ByteArrayWrapper]
  def getStorageName: String = getClass.getSimpleName
}
