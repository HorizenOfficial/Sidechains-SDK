package com.horizen.params


import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion

import java.math.BigInteger
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import com.horizen.cryptolibprovider.utils.CircuitTypes.CircuitTypes
import com.horizen.proposition.{PublicKey25519Proposition, SchnorrProposition, VrfPublicKey}
import sparkz.core.block.Block
import scorex.util.{ModifierId, bytesToId}

trait NetworkParams {
  // Mainchain ProofOfWork parameters:
  val EquihashN: Int
  val EquihashK: Int
  val EquihashCompactSizeLength: Int // CompactSize value length for Equihash solution bytes
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
  val mastersPublicKeys: Seq[SchnorrProposition]
  val circuitType: CircuitTypes
  val signersThreshold: Int
  val certProvingKeyFilePath: String
  val certVerificationKeyFilePath: String
  val calculatedSysDataConstant: Array[Byte]
  val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig]
  val cswProvingKeyFilePath: String
  val cswVerificationKeyFilePath: String
  val sidechainCreationVersion: SidechainCreationVersion
  val isCSWEnabled: Boolean

  val maxHistoryRewritingLength: Int = 100

  // Fee payment params:
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
  val isNonCeasing: Boolean

  val minVirtualWithdrawalEpochLength: Int

  // Sidechain forger restriction
  val restrictForgers: Boolean = false
  val allowedForgersList: Seq[(PublicKey25519Proposition, VrfPublicKey)] = Seq()

  //Max Withdrawal Boxes per certificate
  final val maxWBsAllowed: Int = 3999
}
