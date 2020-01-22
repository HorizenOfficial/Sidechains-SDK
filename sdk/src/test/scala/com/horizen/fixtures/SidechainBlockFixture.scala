package com.horizen.fixtures

import java.time.Instant

import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.{ForgerBox, NoncedBox}
import com.horizen.chain.SidechainBlockInfo
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.customtypes.SemanticallyInvalidTransaction
import com.horizen.params.NetworkParams
import com.horizen.proof.Signature25519
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils._
import com.horizen.vrf.{VRFKeyGenerator, VRFProof, VrfGenerator}
import scorex.core.block.Block
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util.bytesToId


class SemanticallyInvalidSidechainBlock(block: SidechainBlock, companion: SidechainTransactionsCompanion)
  extends SidechainBlock(block.parentId, block.timestamp, block.mainchainBlocks, block.sidechainTransactions, block.forgerPublicKey,  ForgerBoxFixture.generateForgerBox, VrfGenerator.generateProof(9), MerkleTreeFixture.generateRandomMerklePath(9), block.signature, companion) {
  override def semanticValidity(params: NetworkParams): Boolean = false
}

object SidechainBlockFixture extends MainchainBlockReferenceFixture {
  private def firstOrSecond[T](first: T, second: T): T = {
    if (first != null) first else second
  }

  def copy(initialBlock: SidechainBlock,
           parentId: Block.BlockId = null,
           timestamp: Block.Timestamp = -1,
           mainchainBlocks: Seq[MainchainBlockReference] = null,
           sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = null,
           ownerPrivateKey: PrivateKey25519 = null,
           forgerBox: ForgerBox = null,
           vrfProof: VRFProof = null,
           merklePath: MerklePath = null,
           companion: SidechainTransactionsCompanion,
           params: NetworkParams,
           signatureOption: Option[Signature25519] = None,
           basicSeed: Long = 0L
          ): SidechainBlock = {

    SidechainBlock.create(
      firstOrSecond(parentId, initialBlock.parentId),
      Math.max(timestamp, initialBlock.timestamp),
      firstOrSecond(mainchainBlocks, initialBlock.mainchainBlocks),
      firstOrSecond(sidechainTransactions, initialBlock.sidechainTransactions),
      firstOrSecond(ownerPrivateKey, PrivateKey25519Creator.getInstance().generateSecret("seed%d".format(basicSeed).getBytes)),
      firstOrSecond(forgerBox, initialBlock.forgerBox),
      firstOrSecond(vrfProof, initialBlock.vrfProof),
      firstOrSecond(merklePath, initialBlock.merklePath),
      firstOrSecond(companion, initialBlock.companion),
      params,
      signatureOption
    ).get

  }

  def generateSidechainBlock(companion: SidechainTransactionsCompanion,
                             basicSeed: Long = 6543211L,
                             params: NetworkParams = null,
                             genGenesisMainchainBlockHash: Option[Array[Byte]] = None,
                             parentOpt: Option[Block.BlockId] = None,
                             mcParent: Option[ByteArrayWrapper] = None
                            ): SidechainBlock = {
    val vrfProof = VRFKeyGenerator.generate(Array.fill(32)(basicSeed.toByte))._1.prove(Array.fill(32)((basicSeed + 1).toByte))

    val parent = parentOpt.getOrElse(bytesToId(new Array[Byte](32)))
    SidechainBlock.create(
      parent,
      Instant.now.getEpochSecond - 10000,
      Seq(generateMainchainBlockReference(parentOpt = mcParent, blockHash = genGenesisMainchainBlockHash)),
      Seq(),
      PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(basicSeed).getBytes),
      ForgerBoxFixture.generateForgerBox, vrfProof, MerkleTreeFixture.generateRandomMerklePath(basicSeed),
      companion,
      params
    ).get
  }
}

trait SidechainBlockFixture extends MainchainBlockReferenceFixture {
  def generateGenesisBlockInfo(genesisMainchainBlockHash: Option[Array[Byte]] = None, validity: ModifierSemanticValidity = ModifierSemanticValidity.Unknown): SidechainBlockInfo = {
    SidechainBlockInfo(
      1,
      (1L << 32) + 1,
      bytesToId(new Array[Byte](32)),
      validity,
      Seq(com.horizen.chain.byteArrayToMainchainBlockReferenceId(genesisMainchainBlockHash.getOrElse(new Array[Byte](32)))),
      WithdrawalEpochInfo(1, 1)
    )
  }

  def changeBlockInfoValidity(blockInfo: SidechainBlockInfo, validity: ModifierSemanticValidity): SidechainBlockInfo = {
    SidechainBlockInfo(
      blockInfo.height,
      blockInfo.score,
      blockInfo.parentId,
      validity,
      blockInfo.mainchainBlockReferenceHashes,
      WithdrawalEpochInfo(blockInfo.withdrawalEpochInfo.epoch, blockInfo.withdrawalEpochInfo.lastEpochIndex)
    )
  }

  def generateBlockInfo(block: SidechainBlock,
                        parentBlockInfo: SidechainBlockInfo,
                        params: NetworkParams,
                        customScore: Option[Long] = None,
                        validity: ModifierSemanticValidity = ModifierSemanticValidity.Unknown): SidechainBlockInfo = {
    SidechainBlockInfo(
      parentBlockInfo.height + 1,
      customScore.getOrElse(parentBlockInfo.score + (parentBlockInfo.mainchainBlockReferenceHashes.size.toLong << 32) + 1),
      block.parentId,
      validity,
      SidechainBlockInfo.mainchainReferencesFromBlock(block),
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, parentBlockInfo.withdrawalEpochInfo, params)
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

  def generateNextSidechainBlock(sidechainBlock: SidechainBlock, companion: SidechainTransactionsCompanion, params: NetworkParams, basicSeed: Long = 123177L): SidechainBlock = {
    SidechainBlockFixture.copy(sidechainBlock,
      parentId = sidechainBlock.id,
      timestamp = sidechainBlock.timestamp + 10,
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