package io.horizen.block

import java.math.BigInteger
import com.google.common.primitives.{Bytes, Ints}
import com.horizen.utils.BytesUtils
import org.bouncycastle.crypto.digests.Blake2bDigest

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer

// For MainNet and TestNet N = 200, K = 9
// For RegTest N = 48, K = 5

class Equihash(val N: Int, val K: Int) {

  val IndicesPerHashOutput: Int = 512 / N
  val HashOutputLength: Int = IndicesPerHashOutput * N / 8
  val CollisionBitLength: Int = N / (K + 1)
  val CollisionByteLength: Int = (CollisionBitLength + 7) / 8
  val HashLength: Int = (K + 1) * CollisionByteLength
  val SolutionWidth: Int = (1 << K) * (CollisionBitLength + 1) / 8
  val FinalFullWidth: Int = 2 * CollisionByteLength + 4 * (1 << K)


  private class FullStepRow {
    var hash: Array[Byte] = new Array[Byte](FinalFullWidth)

    def this(hashIn: Array[Byte], hashOutLen: Int, bitLen: Int, index: Int) {
      this()
      assert(hashOutLen + 3 <= FinalFullWidth)

      Array.copy(Equihash.expandArray(hashIn, hashOutLen, bitLen), 0, hash, 0, hashOutLen)
      val indexBytes: Array[Byte] = Ints.toByteArray(index)
      Array.copy(indexBytes, 0, hash, hashOutLen, 4)
    }

    def this(a: FullStepRow, b: FullStepRow, len: Int, lenIndices: Int, trim: Int) {
      this()
      hash = a.hash.clone()

      assert(len + lenIndices <= FinalFullWidth)
      assert(len - trim + (2 * lenIndices) <= FinalFullWidth)

      var i: Int = trim
      while (i < len) {
        hash(i - trim) = (a.hash(i) ^ b.hash(i)).toByte
        i += 1
      }

      if (a.indicesBefore(b, len, lenIndices)) {
        Array.copy(a.hash, len, hash, len - trim, lenIndices)
        Array.copy(b.hash, len, hash, len - trim + lenIndices, lenIndices)
      } else {
        Array.copy(b.hash, len, hash, len - trim, lenIndices)
        Array.copy(a.hash, len, hash, len - trim + lenIndices, lenIndices)
      }
    }

    def indicesBefore(fullStepRow: FullStepRow, len: Int, lenIndices: Int): Boolean = {
      var i: Int = len
      while (i < len + lenIndices) {
        val current = BytesUtils.getInt(hash, i)
        val before = BytesUtils.getInt(fullStepRow.hash, i)
        if (current < before)
          return true
        else if (current > before) {
          return false
        }
        i += 4
      }
      false
    }

    def isZero(len: Int): Boolean = {
      var i: Int = 0
      while (i < len) {
        if (hash(i) != 0)
          return false
        i += 1
      }
      true
    }
  }

  // Сhecks if first 'len' bytes of hash are equal
  private def hasCollision(a: FullStepRow, b: FullStepRow, len: Int): Boolean = {
    var i: Int = 0
    while (i < len) {
      if (a.hash(i) != b.hash(i))
        return false
      i += 1
    }
    true
  }

  // Checks if the intersection of a.indices and b.indices is empty
  private def distinctIndices(a: FullStepRow, b: FullStepRow, len: Int, lenIndices: Int): Boolean = {
    var i: Int = 0
    while (i < lenIndices) {
      var j: Int = 0
      while (j < lenIndices) {
        if (BytesUtils.getInt(a.hash, len + i) == BytesUtils.getInt(b.hash, len + j))
          return false
        j += 4
      }
      i += 4
    }
    true
  }

  // Generates base digest state with Horizen Mainchain personalization bytes and updates with msg (usually header bytes without solution)
  def checkEquihashSolution(msgBytes: Array[Byte], solution: Array[Byte]): Boolean = {
    val b2digest: Blake2bDigest = new Blake2bDigest(null, HashOutputLength, null,
      Bytes.concat("ZcashPoW".getBytes(StandardCharsets.UTF_8), BytesUtils.reverseBytes(Ints.toByteArray(N)), BytesUtils.reverseBytes(Ints.toByteArray(K))))
    b2digest.update(msgBytes, 0, msgBytes.length)

    checkEquihashSolution(b2digest, solution)
  }

  def checkEquihashSolution(b2digest: Blake2bDigest, solution: Array[Byte]): Boolean = {
    if(b2digest == null || solution == null)
      throw new IllegalArgumentException("Invalid parameters!")
    if (solution.length != SolutionWidth)
      return false

    var X: ArrayBuffer[FullStepRow] = new ArrayBuffer[FullStepRow](1 << K)

    for (i: Int <- Equihash.getIndicesFromMinimal(solution, CollisionBitLength)) {
      var hashOutput: Array[Byte] = Equihash.generateHash(b2digest, i / IndicesPerHashOutput, HashOutputLength)
      val inStartingPos: Int = (i % IndicesPerHashOutput) * N / 8
      X.append(new FullStepRow(hashOutput.slice(inStartingPos, inStartingPos + N / 8), HashLength, CollisionBitLength, i))
    }

    var hashLen: Int = HashLength
    var lenIndices: Int = 4 // size of Int
    while (X.size > 1) {
      var Xc: ArrayBuffer[FullStepRow] = new ArrayBuffer[FullStepRow]()
      var i: Int = 0
      while (i < X.size) {
        if (!hasCollision(X(i), X(i + 1), CollisionByteLength))
          return false
        if (X(i + 1).indicesBefore(X(i), hashLen, lenIndices))
          return false
        if (!distinctIndices(X(i), X(i + 1), hashLen, lenIndices))
          return false

        Xc.append(new FullStepRow(X(i), X(i + 1), hashLen, lenIndices, CollisionByteLength))
        i += 2
      }

      X = Xc
      hashLen -= CollisionByteLength
      lenIndices *= 2
    }

    assert(X.size == 1)
    X(0).isZero(hashLen)
  }
}


object Equihash {

  def getIndicesFromMinimal(minimal: Array[Byte], collisionBitLength: Int): Array[Int] = {
    assert(((collisionBitLength+1)+7)/8 <= 4)
    val lenIndices: Int = 8*4*minimal.length/(collisionBitLength+1)
    val bytePad: Int = 4 - ((collisionBitLength+1)+7)/8
    var arr: Array[Byte] = expandArray(minimal, lenIndices, collisionBitLength+1, bytePad)

    var res: ArrayBuffer[Int] = new ArrayBuffer[Int](lenIndices/4)
    var i: Int = 0
    while(i < lenIndices) {
      res.append(BytesUtils.getInt(arr, i))
      i += 4
    }
    res.toArray
  }

  def getMinimalFromIndices(indices: Array[Int], colissionBitLength: Int): Array[Byte] = {
    assert(((colissionBitLength+1)+7)/8 <= 4)
    val lenIndices: Int = indices.length * 4
    val minLen: Int = (colissionBitLength+1)*lenIndices/(8*4)
    val bytePad: Int = 4 - ((colissionBitLength+1)+7)/8

    var array: Array[Byte] = new Array[Byte](lenIndices)
    for(i: Int <- indices.indices) {
      Array.copy(Ints.toByteArray(indices(i)), 0, array, i * 4, 4)
    }

    compressArray(array, minLen, colissionBitLength+1, bytePad)
  }

  def expandArray(in: Array[Byte], outLen: Int, bitLen: Int, bytePad: Int = 0): Array[Byte] = {
    assert(bitLen >= 8)
    assert(8*4 >= 7+bitLen)
    val wordMask = BigInteger.ONE.shiftLeft(8*4).subtract(BigInteger.ONE)

    val outWidth: Int = (bitLen+7)/8 + bytePad
    assert(outLen == 8*outWidth*in.length/bitLen)
    val out = new Array[Byte](outLen)

    val bitLenMask: BigInteger = BigInteger.valueOf((1 << bitLen) - 1)

    // The acc_bits least-significant bits of acc_value represent a bit sequence
    // in big-endian order.
    var accBits: Int = 0
    var accValue: BigInteger = BigInteger.ZERO

    var j: Int = 0
    for (i <- in.indices) {
      accValue = accValue.shiftLeft(8).and(wordMask).or(BigInteger.valueOf((in(i) & 0xFF).toLong))
      accBits += 8

      // When we have bitLen or more bits in the accumulator, write the next
      // output element.
      if (accBits >= bitLen) {
        accBits -= bitLen

        for(x <- bytePad until outWidth) {
          val left: BigInteger = accValue.shiftRight(accBits+(8*(outWidth-x-1)))
          val right: BigInteger = bitLenMask.shiftRight(8*(outWidth-x-1)).and(BigInteger.valueOf(0xFF))
          out(j+x) = left.and(right).byteValue()
        }
        j += outWidth
      }
    }
    out
  }

  def compressArray(in: Array[Byte], outLen: Int, bitLen: Int, bytePad: Int = 0): Array[Byte] = {
    assert(bitLen >= 8)
    assert(8*4 >= 7+bitLen)
    val wordMask = BigInteger.ONE.shiftLeft(8*4).subtract(BigInteger.ONE)

    val inWidth = (bitLen + 7) / 8 + bytePad
    assert(outLen == bitLen * in.length / (8 * inWidth))
    val out = new Array[Byte](outLen)

    val bitLenMask: BigInteger = BigInteger.valueOf((1 << bitLen) - 1)

    // The acc_bits least-significant bits of acc_value represent a bit sequence in big-endian order.
    var accBits: Int = 0
    var accValue: BigInteger = BigInteger.ZERO

    var j: Int = 0
    for (i <- 0 until outLen) {
      // When we have fewer than 8 bits left in the accumulator, read the next input element.
      if (accBits < 8) {
        accValue = accValue.shiftLeft(bitLen).and(wordMask)
        for (x <- bytePad until inWidth) {
          // Apply bit_len_mask across byte boundaries
          val b = BigInteger.valueOf(in(j+x)).and(bitLenMask.shiftRight(8*(inWidth-x-1)).and(BigInteger.valueOf(0xFF))).shiftLeft(8*(inWidth-x-1))
          accValue = accValue.or(b)
        }
        j += inWidth
        accBits += bitLen
      }

      accBits -= 8
      out(i) = accValue.shiftRight(accBits).and(BigInteger.valueOf(0xFF)).byteValue()
    }
    out
  }

  private def generateHash(baseDigest: Blake2bDigest, index: Int, hashOutputLength: Int): Array[Byte] = {
    if(baseDigest.getDigestSize != hashOutputLength)
      throw new IllegalArgumentException("Blake2bDigest instance has wrong dagest size(%d), expected (%d)".format(baseDigest.getDigestSize, hashOutputLength))

    val digest: Blake2bDigest = new Blake2bDigest(baseDigest)
    digest.update(BytesUtils.reverseBytes(Ints.toByteArray(index)), 0, 4)
    var hash: Array[Byte] = new Array[Byte](hashOutputLength)
    digest.doFinal(hash, 0)
    hash
  }
}
