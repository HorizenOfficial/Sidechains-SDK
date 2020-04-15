package com.horizen.fixtures

import java.lang.{Byte => JByte}
import java.time.Instant
import java.util.{HashMap => JHashMap}

import com.horizen.SidechainTypes
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.customtypes.SemanticallyInvalidTransaction
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.Proposition
import com.horizen.secret.VrfKeyGenerator
import com.horizen.transaction.{SidechainTransaction, TransactionSerializer}
import com.horizen.utils._
import com.horizen.vrf.VrfProofHash
import scorex.core.block.Block
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.bytesToId

import scala.util.Random


class SemanticallyInvalidSidechainBlock(block: SidechainBlock, companion: SidechainTransactionsCompanion)
  extends SidechainBlock(block.parentId, block.timestamp, block.mainchainBlocks, block.sidechainTransactions, block.forgerBox, block.vrfProof, block.vrfProofHash, block.merklePath, block.signature, companion) {
  override def semanticValidity(params: NetworkParams): Boolean = false
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
           mainchainBlocks: Seq[MainchainBlockReference] = null,
           sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = null,
           forgerBoxData: (ForgerBox, ForgerBoxGenerationMetadata) = null,
           vrfProof: VrfProof = null,
           vrfProofHash: VrfProofHash = null,
           merklePath: MerklePath = null,
           companion: SidechainTransactionsCompanion,
           params: NetworkParams,
           signatureOption: Option[Signature25519] = None,
           basicSeed: Long = 0L
          ): SidechainBlock = {


    val (forgingBox, forgerMetadata) = firstOrSecond(forgerBoxData, ForgerBoxFixture.generateForgerBox(basicSeed))

    SidechainBlock.create(
      firstOrSecond(parentId, initialBlock.parentId),
      Math.max(timestamp, initialBlock.timestamp),
      firstOrSecond(mainchainBlocks, initialBlock.mainchainBlocks),
      firstOrSecond(sidechainTransactions, initialBlock.sidechainTransactions),
      forgerMetadata.rewardSecret,
      forgingBox,
      firstOrSecond(vrfProof, initialBlock.vrfProof),
      firstOrSecond(vrfProofHash, initialBlock.vrfProofHash),
      firstOrSecond(merklePath, initialBlock.merklePath),
      firstOrSecond(companion, sidechainTransactionsCompanion),
      params,
      signatureOption
    ).get

  }

  def generateSidechainBlock(companion: SidechainTransactionsCompanion,
                             basicSeed: Long = 6543211L,
                             params: NetworkParams = null,
                             genGenesisMainchainBlockHash: Option[Array[Byte]] = None,
                             parentOpt: Option[Block.BlockId] = None,
                             mcParent: Option[ByteArrayWrapper] = None,
                             timestamp: Option[Block.Timestamp] = None
                            ): SidechainBlock = {
    val (forgerBox, forgerMetadata) = ForgerBoxFixture.generateForgerBox(basicSeed)
    val vrfKey = VrfKeyGenerator.getInstance().generateSecret(Array.fill(32)(basicSeed.toByte))
    val vrfMessage = "Some non random string as input".getBytes
    val vrfProof = vrfKey.prove(vrfMessage)
    val vrfProofHash = vrfProof.proofToVRFHash(vrfKey.publicImage(), vrfMessage)

    val parent = parentOpt.getOrElse(bytesToId(new Array[Byte](32)))
    SidechainBlock.create(
      parent,
      timestamp.getOrElse(Instant.now.getEpochSecond - 10000),
      Seq(generateMainchainBlockReference(parentOpt = mcParent, blockHash = genGenesisMainchainBlockHash)),
      Seq(),
      forgerMetadata.rewardSecret,
      forgerBox,
      vrfProof,
      vrfProofHash,
      MerkleTreeFixture.generateRandomMerklePath(basicSeed),
      companion,
      params
    ).get
  }
}

trait SidechainBlockFixture extends MainchainBlockReferenceFixture {
  def generateGenesisBlockInfo(genesisMainchainBlockHash: Option[Array[Byte]] = None,
                               validity: ModifierSemanticValidity = ModifierSemanticValidity.Unknown,
                               timestamp: Option[Block.Timestamp] = None,
                               vrfProof: VrfProof = VrfGenerator.generateProof(42),
                               vrfProofHash: VrfProofHash = VrfGenerator.generateProofHash(34)
                              ): SidechainBlockInfo = {
    SidechainBlockInfo(
      1,
      (1L << 32) + 1,
      bytesToId(new Array[Byte](32)),
      timestamp.getOrElse(Random.nextLong()),
      validity,
      Seq(com.horizen.chain.byteArrayToMainchainBlockReferenceId(genesisMainchainBlockHash.getOrElse(new Array[Byte](32)))),
      WithdrawalEpochInfo(1, 1),
      vrfProof,
      vrfProofHash
    )
  }

  def changeBlockInfoValidity(blockInfo: SidechainBlockInfo, validity: ModifierSemanticValidity): SidechainBlockInfo = {
    blockInfo.copy(semanticValidity = validity)
  }

  def generateBlockInfo(block: SidechainBlock,
                        parentBlockInfo: SidechainBlockInfo,
                        params: NetworkParams,
                        customScore: Option[Long] = None,
                        validity: ModifierSemanticValidity = ModifierSemanticValidity.Unknown,
                        timestamp: Option[Block.Timestamp] = None): SidechainBlockInfo = {
    SidechainBlockInfo(
      parentBlockInfo.height + 1,
      customScore.getOrElse(parentBlockInfo.score + (parentBlockInfo.mainchainBlockReferenceHashes.size.toLong << 32) + 1),
      block.parentId,
      block.timestamp,
      validity,
      SidechainBlockInfo.mainchainReferencesFromBlock(block),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, parentBlockInfo.withdrawalEpochInfo, params),
      VrfGenerator.generateProof(parentBlockInfo.height),
      VrfGenerator.generateProofHash(parentBlockInfo.timestamp)
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
    (1 until count).foldLeft((Seq(firstBlock), firstBlock.mainchainBlocks.last.hash)) { (acc, i) =>
      val generatedSeq = acc._1
      val lastMc = acc._2
      val lastBlock = generatedSeq.last
      val newSeq: Seq[SidechainBlock] = generatedSeq :+ SidechainBlockFixture.copy(lastBlock,
                                                                                    parentId = lastBlock.id,
                                                                                    timestamp = Instant.now.getEpochSecond - 1000 + i * 10,
                                                                                    mainchainBlocks = Seq(generateMainchainBlockReference(Some(byteArrayToWrapper(lastMc)))),
                                                                                    sidechainTransactions = Seq(),
                                                                                    companion = companion,
                                                                                    params = params,
                                                                                    basicSeed = basicSeed)
      (newSeq, newSeq.last.mainchainBlocks.last.hash)
    }._1
  }

  val blockGenerationDelta = 10

  def generateNextSidechainBlock(sidechainBlock: SidechainBlock, companion: SidechainTransactionsCompanion, params: NetworkParams, basicSeed: Long = 123177L): SidechainBlock = {
    SidechainBlockFixture.copy(sidechainBlock,
      parentId = sidechainBlock.id,
      timestamp = sidechainBlock.timestamp + blockGenerationDelta,
      mainchainBlocks = Seq(),
      sidechainTransactions = Seq(),
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
      sidechainTransactions = Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]](
        new SemanticallyInvalidTransaction(sidechainBlock.timestamp - 100).asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]]),
      companion = companion,
      params = params,
      basicSeed = basicSeed)
  }
}