package com.horizen.fixtures

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.block.MainchainHeader
import com.horizen.utils.{BytesUtils, VarInt}

// Just for PoW verification testing
case class MainchainHeaderForPoWTest(override val bits: Int, precalculatedHash: Array[Byte], override val hashPrevBlock: Array[Byte] = null, override val time: Int = 0
                                    ) extends MainchainHeader(null, 0, hashPrevBlock,
  null, null, time, bits, null, null) {
  override lazy val hash = precalculatedHash
}


trait MainchainHeaderFixture {

  def mainchainHeaderToBytes(obj: MainchainHeader): Array[Byte] = {
    Bytes.concat(
      BytesUtils.reverseBytes(Ints.toByteArray(obj.version)),
      BytesUtils.reverseBytes(obj.hashPrevBlock),
      BytesUtils.reverseBytes(obj.hashMerkleRoot),
      BytesUtils.reverseBytes(obj.hashScTxsCommitment),
      BytesUtils.reverseBytes(Ints.toByteArray(obj.time)),
      BytesUtils.reverseBytes(Ints.toByteArray(obj.bits)),
      BytesUtils.reverseBytes(obj.nonce),
      BytesUtils.fromVarInt(new VarInt(obj.solution.length, VarInt.getVarIntSize(obj.solution.length))),
      obj.solution
    )
  }

  def getHeaderWithPoW(bits: Int, hash: Array[Byte]) : MainchainHeaderForPoWTest = {
    MainchainHeaderForPoWTest(bits, hash)
  }

  def changeVersion(headerBytes: Array[Byte], newVerion: Int): Array[Byte] = {
    var res = headerBytes.clone()
    System.arraycopy(BytesUtils.reverseBytes(Ints.toByteArray(newVerion)), 0, res, 0, 4)
    res
  }

  def changeMerkleRoot(headerBytes: Array[Byte], newMerkleRoot: Array[Byte]): Array[Byte] = {
    if(newMerkleRoot.length != 32)
      throw new IllegalArgumentException("New merkle root length expected to be 32, instead %d retrieved.".format(newMerkleRoot.length))

    var res = headerBytes.clone()
    System.arraycopy(BytesUtils.reverseBytes(newMerkleRoot), 0, res, 36, 32)
    res
  }

  def changHashSCMerkleRootsMap(headerBytes: Array[Byte], newSCMapMerkleRootHash: Array[Byte], isHeaderWithSCMap: Boolean = false): Array[Byte] = {
    if(!isHeaderWithSCMap)
      throw new IllegalArgumentException("SC Merkle root hash field only expected in header with isHeaderWithSCMap = true.")
    if(newSCMapMerkleRootHash.length != 32)
      throw new IllegalArgumentException("New SCMap merkle root hash length expected to be 32, instead %d retrieved.".format(newSCMapMerkleRootHash.length))

    var res = headerBytes.clone()
    System.arraycopy(BytesUtils.reverseBytes(newSCMapMerkleRootHash), 0, res, 100, 32)
    res
  }

  def changeTime(headerBytes: Array[Byte], newTime: Int, isHeaderWithSCMap: Boolean = false): Array[Byte] = {
    var res = headerBytes.clone()
    var destPos: Int = 100
    if(isHeaderWithSCMap)
      destPos += 32

    System.arraycopy(BytesUtils.reverseBytes(Ints.toByteArray(newTime)), 0, res, 0, 4)
    res
  }

  def changeBits(headerBytes: Array[Byte], newBits: Int, isHeaderWithSCMap: Boolean = false): Array[Byte] = {
    var res = headerBytes.clone()
    var destPos: Int = 104
    if(isHeaderWithSCMap)
      destPos += 32

    System.arraycopy(BytesUtils.reverseBytes(Ints.toByteArray(newBits)), 0, res, 0, 4)
    res
  }

  def changeNonce(headerBytes: Array[Byte], newNonce: Array[Byte], isHeaderWithSCMap: Boolean = false): Array[Byte] = {
    if(newNonce.length != 32)
      throw new IllegalArgumentException("New nonce length expected to be 32, instead %d retrieved.".format(newNonce.length))

    var destPos: Int = 108
    if(isHeaderWithSCMap)
      destPos += 32

    var res = headerBytes.clone()
    System.arraycopy(BytesUtils.reverseBytes(newNonce), 0, res, destPos, 32)
    res
  }

  // Note: newSolution is just a solution bytes without VarIntLength bytes
  def changeSolution(headerBytes: Array[Byte], newSolution: Array[Byte], isHeaderWithSCMap: Boolean = false): Array[Byte] = {
    var toPos = 140
    if(isHeaderWithSCMap)
      toPos += 32

    var headerWithoutEquihashSolution = headerBytes.slice(0,toPos)

    val lengthLE: Int = BytesUtils.getReversedInt(Ints.toByteArray(newSolution.length), 0);

    Bytes.concat(
      headerWithoutEquihashSolution,
      BytesUtils.fromVarInt(new VarInt(VarInt.getVarIntSize(lengthLE), lengthLE)), // reversed VarInt for newSolution
      newSolution
    )
  }
}
