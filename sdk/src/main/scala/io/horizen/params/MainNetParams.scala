package io.horizen.params

import com.horizen.commitmenttreenative.CustomBitvectorElementsConfig
import io.horizen.block.SidechainCreationVersions.{SidechainCreationVersion, SidechainCreationVersion1}
import io.horizen.cryptolibprovider.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit}
import io.horizen.cryptolibprovider.utils.CumulativeHashFunctions
import io.horizen.proposition.{PublicKey25519Proposition, SchnorrProposition, VrfPublicKey}
import sparkz.core.block.Block
import sparkz.util.{ModifierId, bytesToId}

import java.math.BigInteger
import scala.concurrent.duration._

case class MainNetParams(
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
                          override val circuitType: CircuitTypes = NaiveThresholdSignatureCircuit,
                          override val signersThreshold: Int = 0,
                          override val certProvingKeyFilePath: String = "",
                          override val certVerificationKeyFilePath: String = "",
                          override val calculatedSysDataConstant: Array[Byte] = new Array[Byte](32),
                          override val initialCumulativeCommTreeHash: Array[Byte] = new Array[Byte]
                          (CumulativeHashFunctions.hashLength()),
                          override val scCreationBitVectorCertificateFieldConfigs: Seq[CustomBitvectorElementsConfig] = Seq(),
                          override val cswProvingKeyFilePath: String = "",
                          override val cswVerificationKeyFilePath: String = "",
                          override val sc2ScProvingKeyFilePath: Option[String] = None,
                          override val sc2ScVerificationKeyFilePath: Option[String] = None,
                          override val restrictForgers: Boolean = false,
                          override val allowedForgersList: Seq[(PublicKey25519Proposition, VrfPublicKey)] = Seq(),
                          override val sidechainCreationVersion: SidechainCreationVersion = SidechainCreationVersion1,
                          override val chainId: Long = 33333333,
                          override val isCSWEnabled: Boolean = true,
                          override val isNonCeasing: Boolean = false,
                          override val isHandlingTransactionsEnabled: Boolean = true
                        ) extends NetworkParams {
  override val EquihashN: Int = 200
  override val EquihashK: Int = 9
  override val EquihashCompactSizeLength: Int = 3
  override val EquihashSolutionLength: Int = 1344

  override val powLimit: BigInteger = new BigInteger("0007ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)
  override val nPowAveragingWindow: Int = 17
  override val nPowMaxAdjustDown: Int = 32 // 32% adjustment down
  override val nPowMaxAdjustUp: Int = 16 // 16% adjustment up
  override val nPowTargetSpacing: Int = 150 // 2.5 * 60

  override val minVirtualWithdrawalEpochLength: Int = 100
}
