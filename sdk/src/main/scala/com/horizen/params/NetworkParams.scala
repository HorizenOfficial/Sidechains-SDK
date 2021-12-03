package com.horizen.params

import java.math.BigInteger

import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import com.horizen.proposition.SchnorrProposition
import scorex.core.block.Block
import scorex.util.{ModifierId, bytesToId}

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
  val sidechainId: Array[Byte] // Note: we expect to have sidechain id in LittleEndian as in the MC
  val sidechainGenesisBlockId: ModifierId
  val sidechainGenesisBlockParentId: ModifierId = bytesToId(new Array[Byte](32))
  val signersPublicKeys: Seq[SchnorrProposition]
  val signersThreshold: Int
  val certProvingKeyFilePath: String
  val certVerificationKeyFilePath: String
  val calculatedSysDataConstant: Array[Byte]
  val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig]
  val cswProvingKeyFilePath: String
  val cswVerificationKeyFilePath: String

  val maxHistoryRewritingLength: Int = 100

  // fee payment params:
  final val forgerBlockFeeCoefficient: Double = 0.7 // forger portion of fees for the submitted Block

  // Sidechain genesis params:
  val genesisMainchainBlockHash: Array[Byte] // hash of the block which include SidechainCreationTx for current SC
  val parentHashOfGenesisMainchainBlock: Array[Byte] // hash of the block which are parent for genesis MainchainBlock
  val genesisPoWData: Seq[(Int, Int)] // Tuples with timestamps and bits values of <nPowAveragingWindow> blocks up-to <genesisMainchainBlockHash> block. From oldest MC block to genesis one.
  val mainchainCreationBlockHeight: Int // Height of the block which include SidechainCreationTx for current SC
  val sidechainGenesisBlockTimestamp: Block.Timestamp
  val withdrawalEpochLength: Int
  val consensusSecondsInSlot: Int
  val consensusSlotsInEpoch: Int
  val initialCumulativeCommTreeHash: Array[Byte] // CumulativeCommTreeHash value before genesis block
}
