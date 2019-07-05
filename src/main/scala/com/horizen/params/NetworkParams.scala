package com.horizen.params

import java.math.BigInteger

trait NetworkParams {
  // Mainchain ProofOfWork parameters:
  val EquihashN: Int
  val EquihashK: Int
  val EquihashVarIntLength: Int // VarInt value length for Equihash solution bytes
  val EquihashSolutionLength: Int // solution bytes length

  val powLimit: BigInteger

  val nPowAveragingWindow: Int
  val nPowMaxAdjustDown: Int
  val nPowMaxAdjustUp: Int
  val nPowTargetSpacing: Int
  final def averagingWindowTimespan: Int = nPowAveragingWindow * nPowTargetSpacing
  final def MinActualTimespan: Int = (averagingWindowTimespan * (100 - nPowMaxAdjustUp  )) / 100
  final def MaxActualTimespan: Int = (averagingWindowTimespan * (100 + nPowMaxAdjustDown)) / 100
  final def nMedianTimeSpan: Int = 11


  // Sidechain params:
  val zeroHashBytes: Array[Byte] = new Array[Byte](32)
  val sidechainId: Array[Byte]


  // Sidechain genesis params:
  val genesisMainchainBlockHash: Array[Byte] = new Array[Byte](32) // hash of the block which include SidechainCreationTx for current SC
  val genesisPoWData: List[Tuple2[Int, Int]] = List() // Tuple2 with timestamps and bits values of <nPowAveragingWindow> blocks up-to <genesisMainchainBlockHash> block. From oldest MC block to genesis one.
}
