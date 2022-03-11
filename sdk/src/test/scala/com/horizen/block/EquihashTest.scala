package com.horizen.block

import java.util

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.utils.BytesUtils
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

class EquihashTest extends JUnitSuite {

  @Test
  def EquihashTest_ExpandArray(): Unit = {
    var bitLen: Int = 0
    var bytePad: Int  = 0
    var compact: Array[Byte] = null
    var expanded: Array[Byte] = null
    var out: Array[Byte] = null


    // Test 1: 8 11-bit chunks, all-ones
    bitLen = 11
    bytePad = 0
    compact = BytesUtils.fromHexString("ffffffffffffffffffffff")
    expanded = BytesUtils.fromHexString("07ff07ff07ff07ff07ff07ff07ff07ff")

    out = Equihash.expandArray(compact, expanded.length, bitLen, bytePad)
    assertEquals("[8 11-bit chunks, all-ones] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(expanded)),
      true, util.Arrays.equals(expanded, out))


    // Test 2: 8 21-bit chunks, alternating 1s and 0s
    bitLen = 21
    bytePad = 0
    compact = BytesUtils.fromHexString("aaaaad55556aaaab55555aaaaad55556aaaab55555")
    expanded = BytesUtils.fromHexString("155555155555155555155555155555155555155555155555")

    out = Equihash.expandArray(compact, expanded.length, bitLen, bytePad)
    assertEquals("[8 21-bit chunks, alternating 1s and 0s] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(expanded)),
      true, util.Arrays.equals(expanded, out))


    // Test 3: 8 21-bit chunks, based on example in the spec
    bitLen = 21
    bytePad = 0
    compact = BytesUtils.fromHexString("000220000a7ffffe00123022b38226ac19bdf23456")
    expanded = BytesUtils.fromHexString("0000440000291fffff0001230045670089ab00cdef123456")
    out = new Array[Byte](expanded.length)

    out = Equihash.expandArray(compact, expanded.length, bitLen, bytePad)
    assertEquals("[8 21-bit chunks, based on example in the spec] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(expanded)),
      true, util.Arrays.equals(expanded, out))


    // Test 4: 16 14-bit chunks, alternating 11s and 00s
    bitLen = 14
    bytePad = 0
    compact = BytesUtils.fromHexString("cccf333cccf333cccf333cccf333cccf333cccf333cccf333cccf333")
    expanded = BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333")
    out = new Array[Byte](expanded.length)

    out = Equihash.expandArray(compact, expanded.length, bitLen, bytePad)
    assertEquals("[16 14-bit chunks, alternating 11s and 00s] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(expanded)),
      true, util.Arrays.equals(expanded, out))


    // Test 5: 8 11-bit chunks, all-ones, 2-byte padding
    bitLen = 11
    bytePad = 2
    compact = BytesUtils.fromHexString("ffffffffffffffffffffff")
    expanded = BytesUtils.fromHexString("000007ff000007ff000007ff000007ff000007ff000007ff000007ff000007ff")
    out = new Array[Byte](expanded.length)

    out = Equihash.expandArray(compact, expanded.length, bitLen, bytePad)
    assertEquals("[8 11-bit chunks, all-ones, 2-byte padding] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(expanded)),
      true, util.Arrays.equals(expanded, out))
  }

  @Test
  def EquihashTest_CompressArray(): Unit = {
    var bitLen: Int = 0
    var bytePad: Int  = 0
    var compact: Array[Byte] = null
    var expanded: Array[Byte] = null
    var out: Array[Byte] = null


    // Test 1: 8 11-bit chunks, all-ones
    bitLen = 11
    bytePad = 0
    compact = BytesUtils.fromHexString("ffffffffffffffffffffff")
    expanded = BytesUtils.fromHexString("07ff07ff07ff07ff07ff07ff07ff07ff")

    out = Equihash.compressArray(expanded, compact.length, bitLen, bytePad)
    assertEquals("[8 11-bit chunks, all-ones] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(compact)),
      true, util.Arrays.equals(compact, out))


    // Test 2: 8 21-bit chunks, alternating 1s and 0s
    bitLen = 21
    bytePad = 0
    compact = BytesUtils.fromHexString("aaaaad55556aaaab55555aaaaad55556aaaab55555")
    expanded = BytesUtils.fromHexString("155555155555155555155555155555155555155555155555")

    out = Equihash.compressArray(expanded, compact.length, bitLen, bytePad)
    assertEquals("[8 21-bit chunks, alternating 1s and 0s] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(compact)),
      true, util.Arrays.equals(compact, out))


    // Test 3: 8 21-bit chunks, based on example in the spec
    bitLen = 21
    bytePad = 0
    compact = BytesUtils.fromHexString("000220000a7ffffe00123022b38226ac19bdf23456")
    expanded = BytesUtils.fromHexString("0000440000291fffff0001230045670089ab00cdef123456")
    out = new Array[Byte](expanded.length)

    out = Equihash.compressArray(expanded, compact.length, bitLen, bytePad)
    assertEquals("[8 21-bit chunks, based on example in the spec] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(compact)),
      true, util.Arrays.equals(compact, out))


    // Test 4: 16 14-bit chunks, alternating 11s and 00s
    bitLen = 14
    bytePad = 0
    compact = BytesUtils.fromHexString("cccf333cccf333cccf333cccf333cccf333cccf333cccf333cccf333")
    expanded = BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333")
    out = new Array[Byte](expanded.length)

    out = Equihash.compressArray(expanded, compact.length, bitLen, bytePad)
    assertEquals("[16 14-bit chunks, alternating 11s and 00s] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(compact)),
      true, util.Arrays.equals(compact, out))


    // Test 5: 8 11-bit chunks, all-ones, 2-byte padding
    bitLen = 11
    bytePad = 2
    compact = BytesUtils.fromHexString("ffffffffffffffffffffff")
    expanded = BytesUtils.fromHexString("000007ff000007ff000007ff000007ff000007ff000007ff000007ff000007ff")
    out = new Array[Byte](expanded.length)

    out = Equihash.compressArray(expanded, compact.length, bitLen, bytePad)
    assertEquals("[8 11-bit chunks, all-ones, 2-byte padding] Retrieved byte array is '%s'. Expected to be equal to '%s'".format(BytesUtils.toHexString(out), BytesUtils.toHexString(compact)),
      true, util.Arrays.equals(compact, out))
  }

  @Test
  def EquihashTest_GetIndicesFromMinimal(): Unit = {
    var bitLen: Int = 0
    var indices: Array[Int] = null
    var minimal: Array[Byte] = null
    var out: Array[Int] = null


    // Test 1:
    bitLen = 20
    indices = Array(1, 1, 1, 1, 1, 1, 1, 1)
    minimal = BytesUtils.fromHexString("000008000040000200001000008000040000200001")

    out = Equihash.getIndicesFromMinimal(minimal, bitLen)
    assertArrayEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(out.mkString(", "), indices.mkString(", ")), indices, out)


    // Test 2:
    bitLen = 20
    indices = Array(2097151, 2097151, 2097151, 2097151, 2097151, 2097151, 2097151, 2097151)
    minimal = BytesUtils.fromHexString("ffffffffffffffffffffffffffffffffffffffffff")

    out = Equihash.getIndicesFromMinimal(minimal, bitLen)
    assertArrayEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(out.mkString(", "), indices.mkString(", ")), indices, out)


    // Test 3:
    bitLen = 20
    indices = Array(131071, 128, 131071, 128, 131071, 128, 131071, 128)
    minimal = BytesUtils.fromHexString("0ffff8002003fffe000800ffff8002003fffe00080")

    out = Equihash.getIndicesFromMinimal(minimal, bitLen)
    assertArrayEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(out.mkString(", "), indices.mkString(", ")), indices, out)


    // Test 4:
    bitLen = 20
    indices = Array(68, 41, 2097151, 1233, 665, 1023, 1, 1048575)
    minimal = BytesUtils.fromHexString("000220000a7ffffe004d10014c800ffc00002fffff")

    out = Equihash.getIndicesFromMinimal(minimal, bitLen)
    assertArrayEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(out.mkString(", "), indices.mkString(", ")), indices, out)
  }

  @Test
  def EquihashTest_GetMinimalFromIndices(): Unit = {
    var bitLen: Int = 0
    var indices: Array[Int] = null
    var minimal: Array[Byte] = null
    var out: Array[Byte] = null


    // Test 1:
    bitLen = 20
    indices = Array(1, 1, 1, 1, 1, 1, 1, 1)
    minimal = BytesUtils.fromHexString("000008000040000200001000008000040000200001")

    out = Equihash.getMinimalFromIndices(indices, bitLen)
    assertEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(BytesUtils.toHexString(out), BytesUtils.toHexString(minimal)),
      true, util.Arrays.equals(minimal, out))


    // Test 2:
    bitLen = 20
    indices = Array(2097151, 2097151, 2097151, 2097151, 2097151, 2097151, 2097151, 2097151)
    minimal = BytesUtils.fromHexString("ffffffffffffffffffffffffffffffffffffffffff")

    out = Equihash.getMinimalFromIndices(indices, bitLen)
    assertEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(BytesUtils.toHexString(out), BytesUtils.toHexString(minimal)),
      true, util.Arrays.equals(minimal, out))


    // Test 3:
    bitLen = 20
    indices = Array(131071, 128, 131071, 128, 131071, 128, 131071, 128)
    minimal = BytesUtils.fromHexString("0ffff8002003fffe000800ffff8002003fffe00080")

    out = Equihash.getMinimalFromIndices(indices, bitLen)
    assertEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(BytesUtils.toHexString(out), BytesUtils.toHexString(minimal)),
      true, util.Arrays.equals(minimal, out))


    // Test 4:
    bitLen = 20
    indices = Array(68, 41, 2097151, 1233, 665, 1023, 1, 1048575)
    minimal = BytesUtils.fromHexString("000220000a7ffffe004d10014c800ffc00002fffff")

    out = Equihash.getMinimalFromIndices(indices, bitLen)
    assertEquals("Retrieved indices array [%s]. Expected to be equal to [%s]".format(BytesUtils.toHexString(out), BytesUtils.toHexString(minimal)),
      true, util.Arrays.equals(minimal, out))
  }

  @Test
  def EquihashTest_CheckEquihashSolution(): Unit = {
    val N: Int = 96
    val K: Int = 5
    val biLen: Int = N / (K + 1)
    val msg: Array[Byte] = "Equihash is an asymmetric PoW based on the Generalised Birthday problem.".getBytes("utf-8")
    var nonce: Array[Byte] = new Array[Byte](32) // original: uint256 V = ArithToUint256(arith_uint256(1));
    nonce(0) = 1
    val equihash: Equihash = new Equihash(N, K)

    val b2digest: Blake2bDigest = new Blake2bDigest(null, 512 / N * N / 8, null,
      Bytes.concat("ZcashPoW".getBytes, BytesUtils.reverseBytes(Ints.toByteArray(N)), BytesUtils.reverseBytes(Ints.toByteArray(K))))
    b2digest.update(msg, 0 , msg.length)
    b2digest.update(nonce, 0 , nonce.length)

    var indices: Array[Int] = null
    var solution: Array[Byte] = null

    // Test 1: Original valid solution
    indices = Array(2261, 15185, 36112, 104243, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 45858, 116805, 92842, 111026, 15972, 115059, 85191, 90330, 68190, 122819,
      81830, 91132, 23460, 49807, 52426, 80391, 69567, 114474, 104973, 122568)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Valid.", true, equihash.checkEquihashSolution(b2digest, solution))


    // Test 2: Change one index (first one)
    indices = Array(2262, 15185, 36112, 104243, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 45858, 116805, 92842, 111026, 15972, 115059, 85191, 90330, 68190, 122819,
      81830, 91132, 23460, 49807, 52426, 80391, 69567, 114474, 104973, 122568)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 3: Swap two arbitrary indices (2261 and 45858)
    indices = Array(45858, 15185, 36112, 104243, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 2261, 116805, 92842, 111026, 15972, 115059, 85191, 90330, 68190, 122819,
      81830, 91132, 23460, 49807, 52426, 80391, 69567, 114474, 104973, 122568)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 4: Reverse the first pair of indices
    indices = Array(15185, 2261, 36112, 104243, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 45858, 116805, 92842, 111026, 15972, 115059, 85191, 90330, 68190, 122819,
      81830, 91132, 23460, 49807, 52426, 80391, 69567, 114474, 104973, 122568)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 5: Swap the first and second pairs of indices
    indices = Array(36112, 104243, 2261, 15185, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 45858, 116805, 92842, 111026, 15972, 115059, 85191, 90330, 68190, 122819,
      81830, 91132, 23460, 49807, 52426, 80391, 69567, 114474, 104973, 122568)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 6: Swap the second-to-last and last pairs of indices
    indices = Array(2261, 15185, 36112, 104243, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 45858, 116805, 92842, 111026, 15972, 115059, 85191, 90330, 68190, 122819,
      81830, 91132, 23460, 49807, 52426, 80391, 104973, 122568, 69567, 114474)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 7: Swap the first half and second half
    indices = Array(15972, 115059, 85191, 90330, 68190, 122819, 81830, 91132, 23460, 49807,
      52426, 80391, 69567, 114474, 104973, 122568, 2261, 15185, 36112, 104243, 23779, 118390,
      118332, 130041, 32642, 69878, 76925, 80080, 45858, 116805, 92842, 111026)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 8:  Sort the indices
    indices = Array(2261, 15185, 15972, 23460, 23779, 32642, 36112, 45858, 49807, 52426,
      68190, 69567, 69878, 76925, 80080, 80391, 81830, 85191, 90330, 91132, 92842, 104243,
      104973, 111026, 114474, 115059, 116805, 118332, 118390, 122568, 122819, 130041)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 9: Duplicate indices
    indices = Array(2261, 2261, 15185, 15185, 36112, 36112, 104243, 104243, 23779, 23779,
      118390, 118390, 118332, 118332, 130041, 130041, 32642, 32642, 69878, 69878, 76925,
      76925, 80080, 80080, 45858, 45858, 116805, 116805, 92842, 92842, 111026, 111026)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 10: Duplicate first half
    indices = Array(2261, 15185, 36112, 104243, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 45858, 116805, 92842, 111026, 2261, 15185, 36112, 104243, 23779, 118390,
      118332, 130041, 32642, 69878, 76925, 80080, 45858, 116805, 92842, 111026)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(b2digest, solution))


    // Test 11: Original valid solution, but invalid digest base state (nonce missed)
    val wrongDigest: Blake2bDigest = new Blake2bDigest(null, 512 / N * N / 8, null,
      Bytes.concat("ZcashPoW".getBytes, BytesUtils.reverseBytes(Ints.toByteArray(N)), BytesUtils.reverseBytes(Ints.toByteArray(K))))
    b2digest.update(msg, 0 , msg.length)

    indices = Array(2261, 15185, 36112, 104243, 23779, 118390, 118332, 130041, 32642, 69878,
      76925, 80080, 45858, 116805, 92842, 111026, 15972, 115059, 85191, 90330, 68190, 122819,
      81830, 91132, 23460, 49807, 52426, 80391, 69567, 114474, 104973, 122568)

    solution = Equihash.getMinimalFromIndices(indices, biLen)
    assertEquals("Solution expected to be Invalid.", false, equihash.checkEquihashSolution(wrongDigest, solution))
  }
}
