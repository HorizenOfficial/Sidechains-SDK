package com.horizen

import com.horizen.utils.ByteArrayWrapper
import supertagged.TaggedType

package object chain {
  object MainchainHeaderHash extends TaggedType[ByteArrayWrapper]
  type MainchainHeaderHash = MainchainHeaderHash.Type
  val mainchainHeaderHashSize = 32

  def byteWrapperToMainchainHeaderHash(wrapper: ByteArrayWrapper): MainchainHeaderHash = MainchainHeaderHash @@ wrapper

  def byteArrayToMainchainHeaderHash(bytes: Array[Byte]): MainchainHeaderHash = MainchainHeaderHash @@ new ByteArrayWrapper(bytes)
}
