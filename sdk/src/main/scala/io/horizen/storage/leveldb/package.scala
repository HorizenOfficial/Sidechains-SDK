package io.horizen.storage

import sparkz.crypto.hash.Blake2b256
import sparkz.util.SparkzEncoding

/**
 * That source code had been copied/modified from ErgoPlatform Project
 */

package object leveldb {
  object Constants {
    val HashLength: Int = 32
    //Batch size used in the SidechainWallet and SidechainState restore method.
    //TODO: Investigate what could be a real good value.
    val BatchSize: Int = 10000
  }

  object Algos extends SparkzEncoding {

    type HF = Blake2b256.type

    val hash: HF = Blake2b256

    @inline def encode(bytes: Array[Byte]): String = encoder.encode(bytes)
  }
}
