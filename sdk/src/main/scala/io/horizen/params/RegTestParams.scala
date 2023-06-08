package io.horizen.params

import io.horizen.block.SidechainCreationVersions.{SidechainCreationVersion, SidechainCreationVersion1}

import java.math.BigInteger
import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.cryptolibprovider.CircuitTypes
import io.horizen.cryptolibprovider.CircuitTypes.CircuitTypes
import io.horizen.cryptolibprovider.utils.CumulativeHashFunctions
import io.horizen.proposition.{PublicKey25519Proposition, SchnorrProposition, VrfPublicKey}
import sparkz.core.block.Block
import sparkz.util.ModifierId
import sparkz.util.bytesToId
import scala.concurrent.duration._

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
                          override val mastersPublicKeys: Seq[SchnorrProposition] = Seq(),
                          override val circuitType: CircuitTypes = CircuitTypes.NaiveThresholdSignatureCircuit,
                          override val signersThreshold: Int = 0,
                          override val certProvingKeyFilePath: String = "",
                          override val certVerificationKeyFilePath: String = "",
                          override val calculatedSysDataConstant: Array[Byte] = new Array[Byte](32),
                          override val initialCumulativeCommTreeHash: Array[Byte] = new Array[Byte](CumulativeHashFunctions.hashLength()),
                          override val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig] = Seq(),
                          override val cswProvingKeyFilePath: String = "",
                          override val cswVerificationKeyFilePath: String = "",
                          override val sc2ScProvingKeyFilePath: Option[String] = None,
                          override val sc2ScVerificationKeyFilePath: Option[String] = None,
                          override val restrictForgers: Boolean = false,
                          override val allowedForgersList: Seq[(PublicKey25519Proposition, VrfPublicKey)] = Seq(),
                          override val sidechainCreationVersion: SidechainCreationVersion = SidechainCreationVersion1,
                          override val chainId: Long = 1111111,
                          override val isCSWEnabled: Boolean = true,
                          override val isNonCeasing: Boolean = false,
                          override val getLogsSizeLimit: Int = 10,
                          override val getLogsQueryTimeout: FiniteDuration = 2.seconds,
                        ) extends NetworkParams {
  override val EquihashN: Int = 48
  override val EquihashK: Int = 5
  override val EquihashCompactSizeLength: Int = 1
  override val EquihashSolutionLength: Int = 36

  override val powLimit: BigInteger = new BigInteger("0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f", 16)
  override val nPowAveragingWindow: Int = 17
  override val nPowMaxAdjustDown: Int = 0 // Turn off adjustment down
  override val nPowMaxAdjustUp: Int = 0 // Turn off adjustment up
  override val nPowTargetSpacing: Int = 150 // 2.5 * 60

  override val minVirtualWithdrawalEpochLength: Int = 10
}
