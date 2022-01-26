package com.horizen.params
import java.math.BigInteger

import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import com.horizen.cryptolibprovider.CumulativeHashFunctions
import com.horizen.proposition.SchnorrProposition
import scorex.core.block.Block
import scorex.util.ModifierId
import scorex.util.bytesToId

case class RegTestParams(
                          override val sidechainId: Array[Byte] = new Array[Byte](32),
                          override val sidechainGenesisBlockId: ModifierId = bytesToId(new Array[Byte](32)),
                          override val genesisMainchainBlockHash: Array[Byte] = new Array[Byte](32),
                          override val parentHashOfGenesisMainchainBlock: Array[Byte] = new Array[Byte](32),
                          override val genesisPoWData: Seq[(Int, Int)] = Seq(),
                          override val mainchainCreationBlockHeight: Int = 1,
                          override val withdrawalEpochLength: Int = 100,
                          override val sidechainGenesisBlockTimestamp: Block.Timestamp = 720 * 120,
                          override val consensusSecondsInSlot: Int = 120,
                          override val consensusSlotsInEpoch: Int = 720,
                          override val signersPublicKeys: Seq[SchnorrProposition] = Seq(),
                          override val signersThreshold: Int = 0,
                          override val certProvingKeyFilePath: String = "",
                          override val certVerificationKeyFilePath: String = "",
                          override val calculatedSysDataConstant: Array[Byte] = new Array[Byte](32),
                          override val initialCumulativeCommTreeHash: Array[Byte] = new Array[Byte](CumulativeHashFunctions.hashLength()),
                          override val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig] = Seq(),
                          override val cswProvingKeyFilePath: String = "",
                          override val cswVerificationKeyFilePath: String = ""
) extends NetworkParams {
  override val EquihashN: Int = 48
  override val EquihashK: Int = 5
  override val EquihashVarIntLength: Int = 1
  override val EquihashSolutionLength: Int = 36

  override val powLimit: BigInteger = new BigInteger("0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f", 16)
  override val nPowAveragingWindow: Int = 17
  override val nPowMaxAdjustDown: Int = 0 // Turn off adjustment down
  override val nPowMaxAdjustUp: Int = 0 // Turn off adjustment up
  override val nPowTargetSpacing: Int = 150 // 2.5 * 60
}
