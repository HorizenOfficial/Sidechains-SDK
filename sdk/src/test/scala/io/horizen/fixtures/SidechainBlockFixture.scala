package io.horizen.fixtures

import java.lang.{Byte => JByte}
import java.time.Instant
import java.util.{HashMap => JHashMap}
import io.horizen.SidechainTypes
import io.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader}
import io.horizen.chain.{MainchainHeaderBaseInfo, MainchainHeaderHash, SidechainBlockInfo, mainchainHeaderHashSize}
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.params.NetworkParams
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.{Proposition, VrfPublicKey}
import io.horizen.secret.{VrfKeyGenerator, VrfSecretKey}
import io.horizen.transaction.TransactionSerializer
import io.horizen.utils._
import io.horizen.utxo.block.SidechainBlock
import io.horizen.utxo.box.{Box, ForgerBox}
import io.horizen.utxo.customtypes.SemanticallyInvalidTransaction
import io.horizen.utxo.transaction.SidechainTransaction
import io.horizen.vrf.VrfOutput
import sparkz.core.block.Block
import sparkz.core.consensus.ModifierSemanticValidity
import sparkz.util.{ModifierId, bytesToId}

import java.nio.charset.StandardCharsets
import scala.util.{Failure, Random, Try}


class SemanticallyInvalidSidechainBlock(block: SidechainBlock, companion: SidechainTransactionsCompanion)
  extends SidechainBlock(block.header, block.sidechainTransactions, block.mainchainBlockReferencesData, block.mainchainHeaders, block.ommers, companion) {
  override def semanticValidity(params: NetworkParams): Try[Unit] = Failure(new Exception("exception"))
}

object SidechainBlockFixture extends MainchainBlockReferenceFixture with CompanionsFixture {
  val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]] = new JHashMap()
  val sidechainTransactionsCompanion = getDefaultTransactionsCompanion

  private def firstOrSecond[T](first: T, second: T): T = {
    if (first != null) first else second
  }

  def copy(initialBlock: SidechainBlock,
           parentId: Block.BlockId = null,
           timestamp: Block.Timestamp = -1,
           mainchainBlocksReferencesData: Seq[MainchainBlockReferenceData] = null,
           sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]] = null,
           mainchainHeaders: Seq[MainchainHeader] = null,
           forgerBoxData: (ForgerBox, ForgerBoxGenerationMetadata) = null,
           vrfProof: VrfProof = null,
           merklePath: MerklePath = null,
           companion: SidechainTransactionsCompanion,
           params: NetworkParams,
           signatureOption: Option[Signature25519] = None,
           basicSeed: Long = 0L
          ): SidechainBlock = {


    val (forgingBox, forgerMetadata) = firstOrSecond(forgerBoxData, ForgerBoxFixture.generateForgerBox(basicSeed))

    SidechainBlock.create(
      firstOrSecond(parentId, initialBlock.parentId),
      SidechainBlock.BLOCK_VERSION,
      Math.max(timestamp, initialBlock.timestamp),
      firstOrSecond(mainchainBlocksReferencesData, initialBlock.mainchainBlockReferencesData),
      firstOrSecond(sidechainTransactions, initialBlock.sidechainTransactions),
      firstOrSecond(mainchainHeaders, initialBlock.mainchainHeaders),
      Seq(), // TODO: ommers support
      forgerMetadata.blockSignSecret,
      forgerMetadata.forgingStakeInfo,
      firstOrSecond(vrfProof, initialBlock.header.vrfProof),
      firstOrSecond(merklePath, initialBlock.header.forgingStakeMerklePath),
      initialBlock.header.feePaymentsHash,
      firstOrSecond(companion, sidechainTransactionsCompanion),
      signatureOption
    ).get

  }

  def generateSidechainBlock(companion: SidechainTransactionsCompanion,
                             basicSeed: Long = 6543211L,
                             params: NetworkParams = null,
                             genGenesisMainchainBlockHash: Option[Array[Byte]] = None,
                             parentOpt: Option[Block.BlockId] = None,
                             mcParent: Option[ByteArrayWrapper] = None,
                             timestampOpt: Option[Block.Timestamp] = None,
                             includeReference: Boolean = true,
                             vrfKeysOpt: Option[(VrfSecretKey, VrfPublicKey)] = None,
                             vrfProofOpt: Option[VrfProof] = None
                            ): SidechainBlock = {
    val (forgerBox, forgerMetadata) = ForgerBoxFixture.generateForgerBox(basicSeed, vrfKeysOpt)
    val vrfKey = VrfKeyGenerator.getInstance().generateSecret(Array.fill(32)(basicSeed.toByte))
    val vrfMessage = "Some non random string as input".getBytes(StandardCharsets.UTF_8)
    val vrfProof = vrfProofOpt.getOrElse(vrfKey.prove(vrfMessage).getKey)

    val parent = parentOpt.getOrElse(bytesToId(new Array[Byte](32)))
    val timestamp = timestampOpt.getOrElse(Instant.now.getEpochSecond - 10000)
    val references: Seq[MainchainBlockReference] = if(includeReference)
      Seq(generateMainchainBlockReference(mcParent, genGenesisMainchainBlockHash, new java.util.Random(basicSeed), (timestamp - 100).toInt))
      else
      Seq()

    SidechainBlock.create(
      parent,
      SidechainBlock.BLOCK_VERSION,
      timestamp,
      references.map(_.data),
      Seq(),
      references.map(_.header),
      Seq(), // TODO: ommers suport
      forgerMetadata.blockSignSecret,
      forgerMetadata.forgingStakeInfo,
      vrfProof,
      MerkleTreeFixture.generateRandomMerklePath(basicSeed),
      new Array[Byte](32),
      companion
    ).get
  }
}

trait SidechainBlockFixture extends MainchainBlockReferenceFixture with SidechainBlockHeaderFixture {

  def generateGenesisBlockInfo(genesisMainchainHeaderHash: Option[Array[Byte]] = None,
                               genesisMainchainReferenceDataHeaderHash: Option[Array[Byte]] = None,
                               validity: ModifierSemanticValidity = ModifierSemanticValidity.Unknown,
                               timestamp: Option[Block.Timestamp] = None,
                               vrfOutput: VrfOutput = VrfGenerator.generateVrfOutput(34),
                               initialCumulativeHash: Array[Byte] = FieldElementFixture.generateFieldElement()
                              ): SidechainBlockInfo = {
    val blockId = bytesToId(new Array[Byte](32))
    val mainchainHeaderHash : MainchainHeaderHash = io.horizen.chain.byteArrayToMainchainHeaderHash(genesisMainchainHeaderHash.getOrElse(new Array[Byte](32)))

    SidechainBlockInfo(
      1,
      1,
      bytesToId(new Array[Byte](32)),
      timestamp.getOrElse(Random.nextLong()),
      validity,
      Seq(MainchainHeaderBaseInfo(mainchainHeaderHash, initialCumulativeHash)),
      Seq(mainchainHeaderHash),
      WithdrawalEpochInfo(1, 1),
      Option(vrfOutput),
      blockId
    )
  }

  def changeBlockInfoValidity(blockInfo: SidechainBlockInfo, validity: ModifierSemanticValidity): SidechainBlockInfo = {
    blockInfo.copy(semanticValidity = validity)
  }

  def generateBlockInfo(block: SidechainBlock,
                        parentBlockInfo: SidechainBlockInfo,
                        params: NetworkParams,
                        previousCumulativeHash: Array[Byte],
                        customScore: Option[Long] = None,
                        validity: ModifierSemanticValidity = ModifierSemanticValidity.Unknown,
                        timestamp: Option[Block.Timestamp] = None): SidechainBlockInfo = {

    SidechainBlockInfo(
      parentBlockInfo.height + 1,
      customScore.getOrElse(parentBlockInfo.score + (parentBlockInfo.mainchainHeaderHashes.size.toLong << 32) + 1),
      block.parentId,
      block.timestamp,
      validity,
      MainchainHeaderBaseInfo.getMainchainHeaderBaseInfoSeqFromBlock(block, previousCumulativeHash),
      SidechainBlockInfo.mainchainReferenceDataHeaderHashesFromBlock(block),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block.mainchainBlockReferencesData.size, parentBlockInfo.withdrawalEpochInfo, params),
      Option(VrfGenerator.generateVrfOutput(parentBlockInfo.timestamp)),
      block.parentId
    )
  }


  def generateSidechainBlockSeq(count: Int,
                                companion: SidechainTransactionsCompanion,
                                params: NetworkParams,
                                parentOpt: Option[Block.BlockId] = None,
                                basicSeed: Long = 65432L,
                                mcParent: Option[ByteArrayWrapper] = None): Seq[SidechainBlock] = {
    require(count > 0)
    val firstBlock = SidechainBlockFixture.generateSidechainBlock(companion = companion, basicSeed = basicSeed, params = params, parentOpt = parentOpt, mcParent = mcParent)
    (1 until count).foldLeft((Seq(firstBlock), firstBlock.mainchainHeaders.last.hash)) { (acc, i) =>
      val generatedSeq = acc._1
      val lastMc = acc._2
      val lastBlock = generatedSeq.last
      val refs = Seq(generateMainchainBlockReference(Some(byteArrayToWrapper(lastMc))))
      val newSeq: Seq[SidechainBlock] = generatedSeq :+ SidechainBlockFixture.copy(lastBlock,
                                                                                    parentId = lastBlock.id,
                                                                                    timestamp = Instant.now.getEpochSecond - 1000 + i * 10,
                                                                                    mainchainBlocksReferencesData = refs.map(_.data),
                                                                                    sidechainTransactions = Seq(),
                                                                                    mainchainHeaders = refs.map(_.header),
                                                                                    companion = companion,
                                                                                    params = params,
                                                                                    basicSeed = basicSeed)
      (newSeq, newSeq.last.mainchainHeaders.last.hash)
    }._1
  }

  val blockGenerationDelta = 10

  def generateNextSidechainBlock(sidechainBlock: SidechainBlock,
                                 companion: SidechainTransactionsCompanion,
                                 params: NetworkParams,
                                 basicSeed: Long = 123177L,
                                 timestampDelta: Long = blockGenerationDelta): SidechainBlock = {
    SidechainBlockFixture.copy(sidechainBlock,
      parentId = sidechainBlock.id,
      timestamp = sidechainBlock.timestamp + blockGenerationDelta,
      mainchainBlocksReferencesData = Seq(),
      sidechainTransactions = Seq(),
      mainchainHeaders = Seq(),
      companion = companion,
      params = params,
      basicSeed = basicSeed)
  }

  def createSemanticallyInvalidClone(sidechainBlock: SidechainBlock, companion: SidechainTransactionsCompanion): SidechainBlock = {
    new SemanticallyInvalidSidechainBlock(sidechainBlock, companion)
  }

  // not companion should contain serializer for SemanticallyInvalidTransaction
  def generateNextSidechainBlockWithInvalidTransaction(sidechainBlock: SidechainBlock, companion: SidechainTransactionsCompanion, params: NetworkParams, basicSeed: Long = 12325L): SidechainBlock = {
    SidechainBlockFixture.copy(sidechainBlock,
      parentId = sidechainBlock.id,
      timestamp = sidechainBlock.timestamp + 10,
      sidechainTransactions = Seq[SidechainTransaction[Proposition, Box[Proposition]]](
        new SemanticallyInvalidTransaction().asInstanceOf[SidechainTransaction[Proposition, Box[Proposition]]]),
      companion = companion,
      params = params,
      basicSeed = basicSeed)
  }

  def getRandomBlockId(seed: Long = 1312): ModifierId = {
    val id: Array[Byte] = new Array[Byte](32)
    new Random(seed).nextBytes(id)
    bytesToId(id)
  }
}