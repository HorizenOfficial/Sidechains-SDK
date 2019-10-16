package com.horizen

import com.horizen.utils.ByteArrayWrapper
import supertagged.TaggedType

package object chain {
  object MainchainBlockReferenceId extends TaggedType[ByteArrayWrapper]
  type MainchainBlockReferenceId = MainchainBlockReferenceId.Type
  val mainchainBlockReferenceIdSize = 32

  def byteWrapperToMainchainBlockReferenceId(wrapper: ByteArrayWrapper): MainchainBlockReferenceId = MainchainBlockReferenceId @@ wrapper

  def byteArrayToMainchainBlockReferenceId(bytes: Array[Byte]): MainchainBlockReferenceId = MainchainBlockReferenceId @@ new ByteArrayWrapper(bytes)
}
