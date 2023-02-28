package io.horizen.storage

import com.horizen.utils.ByteArrayWrapper

trait SidechainStorageInfo {
  def lastVersionId : Option[ByteArrayWrapper]
  def getStorageName: String = getClass.getSimpleName
}
