package org.ergoplatform

import scorex.core.utils.ScorexEncoding
import scorex.crypto.hash.Blake2b256

package object db {
  object Constants {
    val HashLength: Int = 32
  }

  object Algos extends ScorexEncoding {

    type HF = Blake2b256.type

    val hash: HF = Blake2b256

    @inline def encode(bytes: Array[Byte]): String = encoder.encode(bytes)
  }
}
