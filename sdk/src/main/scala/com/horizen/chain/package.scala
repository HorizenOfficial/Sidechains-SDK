package com.horizen

import com.horizen.utils.ByteArrayWrapper
import supertagged.TaggedType

package object chain {
  object MainchainBlockReferenceHash extends TaggedType[ByteArrayWrapper]
  type MainchainBlockReferenceHash = MainchainBlockReferenceHash.Type
  val mainchainBlockReferenceHashSize = 32

  def byteWrapperToMainchainBlockReferenceHash(wrapper: ByteArrayWrapper): MainchainBlockReferenceHash = MainchainBlockReferenceHash @@ wrapper

  def byteArrayToMainchainBlockReferenceHash(bytes: Array[Byte]): MainchainBlockReferenceHash = MainchainBlockReferenceHash @@ new ByteArrayWrapper(bytes)
}
