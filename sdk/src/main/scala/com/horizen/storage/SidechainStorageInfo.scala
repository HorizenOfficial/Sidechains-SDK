package com.horizen.storage

import com.horizen.utils.ByteArrayWrapper

trait SidechainStorageInfo {
  def lastVersionId : Option[ByteArrayWrapper]
}
