package com.horizen.storage

import java.io.File

import io.iohk.iodb.LSMStore

object IODBStorageUtil {

  def getStorage(storePath: File) : Storage = {
    storePath.mkdirs()
    new IODBStoreAdapter(new LSMStore(storePath))
  }


}
