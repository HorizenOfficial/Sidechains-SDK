package io.horizen.utils

import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.VLQByteBufferReader

import java.nio.ByteBuffer

/**
 * Extends the DynamicTypedSerializer with the capability of checking that there are no remaining bytes after the
 * parsing of the input bytes array.
 */
trait CheckedCompanion[
    T <: BytesSerializable,
    S <: SparkzSerializer[T]
] extends DynamicTypedSerializer[T, S] {
  override final def parseBytes(bytes: Array[Byte]): T = {
    val reader = new VLQByteBufferReader(ByteBuffer.wrap(bytes))
    val parsedObj = parse(reader)
    val size = reader.remaining
    if (size > 0) {
      throw new IllegalArgumentException(s"Spurious data found in byte stream after obj parsing: $size bytes")
    }
    parsedObj
  }
}
