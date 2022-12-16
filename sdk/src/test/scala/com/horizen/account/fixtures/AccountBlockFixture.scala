package com.horizen.account.fixtures

import java.lang.{Byte => JByte}
import java.time.Instant
import java.util.{HashMap => JHashMap}
import com.horizen.{SidechainTypes, utils}
import com.horizen.account.block.AccountBlock
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.Bloom
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, SidechainBlock}
import com.horizen.chain.{MainchainHeaderBaseInfo, MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.customtypes.SemanticallyInvalidTransaction
import com.horizen.fixtures.SidechainBlockFixture.firstOrSecond
import com.horizen.fixtures.{CompanionsFixture, FieldElementFixture, ForgerBoxFixture, MainchainBlockReferenceFixture, MerkleTreeFixture, VrfGenerator}
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.{PrivateKey25519, VrfKeyGenerator, VrfSecretKey}
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils._
import com.horizen.vrf.VrfOutput
import sparkz.core.block.Block
import sparkz.core.consensus.ModifierSemanticValidity
import scorex.util.{ModifierId, bytesToId}

import java.math.BigInteger
import scala.util.{Failure, Try}


class SemanticallyInvalidAccountBlock(block: AccountBlock, companion: SidechainAccountTransactionsCompanion)
  extends AccountBlock(block.header, block.sidechainTransactions, block.mainchainBlockReferencesData, block.mainchainHeaders, block.ommers, companion) {
  override def semanticValidity(params: NetworkParams): Try[Unit] = Failure(new Exception("exception"))
}

object AccountBlockFixture extends MainchainBlockReferenceFixture with CompanionsFixture {
  val customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]] = new JHashMap()
  val sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion = getDefaultAccountTransactionsCompanion

  private def firstOrSecond[T](first: T, second: T): T = {
    if (first != null) first else second
  }

  def copy(initialBlock: AccountBlock,
           parentId: Block.BlockId = null,
           timestamp: Block.Timestamp = -1,
           mainchainBlocksReferencesData: Seq[MainchainBlockReferenceData] = null,
           sidechainTransactions: Seq[SidechainTypes#SCAT] = null,
           mainchainHeaders: Seq[MainchainHeader] = null,
           vrfProof: VrfProof = null,
           merklePath: MerklePath = null,
           companion: SidechainAccountTransactionsCompanion,
           params: NetworkParams,
           signatureOption: Option[Signature25519] = None,
           basicSeed: Long = 0L
          ): AccountBlock = {

    //val (accountPayment, forgerMetadata) = firstOrSecond(forgerAccountData, ForgerAccountFixture.generateForgerAccount(basicSeed))
    val (accountPayment, forgerMetadata) = ForgerAccountFixture.generateForgerAccountData(basicSeed)


    val ownerPrivateKey : PrivateKey25519 = forgerMetadata.blockSignSecret
    val forgingStakeInfo : ForgingStakeInfo = forgerMetadata.forgingStakeInfo

    AccountBlock.create(
      firstOrSecond(parentId, initialBlock.parentId),
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      Math.max(timestamp, initialBlock.timestamp),
      firstOrSecond(mainchainBlocksReferencesData, initialBlock.mainchainBlockReferencesData),
      firstOrSecond(sidechainTransactions, initialBlock.sidechainTransactions),
      firstOrSecond(mainchainHeaders, initialBlock.mainchainHeaders),
      Seq(), // TODO: ommers support
      ownerPrivateKey,
      forgingStakeInfo,
      firstOrSecond(vrfProof, initialBlock.header.vrfProof),
      firstOrSecond(merklePath, initialBlock.header.forgingStakeMerklePath),
      initialBlock.header.feePaymentsHash,
      initialBlock.header.stateRoot,
      initialBlock.header.receiptsRoot,
      initialBlock.header.forgerAddress,
      initialBlock.header.baseFee,
      initialBlock.header.gasUsed,
      initialBlock.header.gasLimit,
      firstOrSecond(companion, sidechainTransactionsCompanion),
      initialBlock.header.logsBloom,
      signatureOption
    ).get

  }

  def generatePrivateKey25519(seed : Long): PrivateKey25519 = {
    val randomGenerator = new java.util.Random(seed)
    val byteSeed = new Array[Byte](32)
    randomGenerator.nextBytes(byteSeed)
    val propositionKeyPair: utils.Pair[Array[Byte], Array[Byte]] = Ed25519.createKeyPair(byteSeed)
    val ownerKeys: PrivateKey25519 = new PrivateKey25519(propositionKeyPair.getKey, propositionKeyPair.getValue)
    ownerKeys
  }

  def generateAccountBlock(companion: SidechainAccountTransactionsCompanion,
                           basicSeed: Long = 6543211L,
                           params: NetworkParams = null,
                           genGenesisMainchainBlockHash: Option[Array[Byte]] = None,
                           parentOpt: Option[Block.BlockId] = None,
                           mcParent: Option[ByteArrayWrapper] = None,
                           timestampOpt: Option[Block.Timestamp] = None,
                           includeReference: Boolean = true,
                           vrfKeysOpt: Option[(VrfSecretKey, VrfPublicKey)] = None,
                           vrfProofOpt: Option[VrfProof] = None
                            ): AccountBlock = {
    val vrfKey = VrfKeyGenerator.getInstance().generateSecret(Array.fill(32)(basicSeed.toByte))
    val vrfMessage = "Some non random string as input".getBytes
    val vrfProof = vrfProofOpt.getOrElse(vrfKey.prove(vrfMessage).getKey)

    val parent = parentOpt.getOrElse(bytesToId(new Array[Byte](32)))
    val timestamp = timestampOpt.getOrElse(Instant.now.getEpochSecond - 10000)
    val references: Seq[MainchainBlockReference] = if(includeReference)
      Seq(generateMainchainBlockReference(mcParent, genGenesisMainchainBlockHash, new java.util.Random(basicSeed), (timestamp - 100).toInt))
      else
      Seq()

    val (accountPayment, forgerMetadata) = ForgerAccountFixture.generateForgerAccountData(basicSeed, vrfKeysOpt)

    val ownerPrivateKey : PrivateKey25519 = forgerMetadata.blockSignSecret
    val forgingStakeInfo : ForgingStakeInfo = forgerMetadata.forgingStakeInfo
    val forgerAddress: AddressProposition = accountPayment.address
    val baseFee: BigInteger = BigInteger.ZERO // TODO
    val gasUsed: Long = 0; // TODO
    val gasLimit: Long = 0; // TODO
    val logsBloom: Bloom = new Bloom(); // TODO

    AccountBlock.create(
      parent,
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      timestamp,
      references.map(_.data),
      Seq(),
      references.map(_.header),
      Seq(), // TODO: ommers suport
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      MerkleTreeFixture.generateRandomMerklePath(basicSeed),
      new Array[Byte](32),
      new Array[Byte](32),
      new Array[Byte](32),
      forgerAddress,
      baseFee,
      gasUsed, gasLimit,
      companion,
      logsBloom
    ).get
  }
}

trait AccountBlockFixture extends MainchainBlockReferenceFixture with AccountBlockHeaderFixture {

  def generateGenesisBlockInfo(genesisMainchainHeaderHash: Option[Array[Byte]] = None,
                               genesisMainchainReferenceDataHeaderHash: Option[Array[Byte]] = None,
                               validity: ModifierSemanticValidity = ModifierSemanticValidity.Unknown,
                               timestamp: Option[Block.Timestamp] = None,
                               vrfOutput: VrfOutput = VrfGenerator.generateVrfOutput(34),
                               initialCumulativeHash: Array[Byte] = FieldElementFixture.generateFieldElement()
                              ): SidechainBlockInfo = {
    val blockId = bytesToId(new Array[Byte](32))
    val mainchainHeaderHash : MainchainHeaderHash = com.horizen.chain.byteArrayToMainchainHeaderHash(genesisMainchainHeaderHash.getOrElse(new Array[Byte](32)))

    SidechainBlockInfo(
      1,
      1,
      bytesToId(new Array[Byte](32)),
      timestamp.getOrElse(scala.util.Random.nextLong()),
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
      WithdrawalEpochUtils.getWithdrawalEpochInfo(block, parentBlockInfo.withdrawalEpochInfo, params),
      Option(VrfGenerator.generateVrfOutput(parentBlockInfo.timestamp)),
      block.parentId
    )
  }


  def generateAccountBlockSeq(count: Int,
                                companion: SidechainAccountTransactionsCompanion,
                                params: NetworkParams,
                                parentOpt: Option[Block.BlockId] = None,
                                basicSeed: Long = 65432L,
                                mcParent: Option[ByteArrayWrapper] = None): Seq[AccountBlock] = {
    require(count > 0)
    val firstBlock = AccountBlockFixture.generateAccountBlock(
      companion = companion, basicSeed = basicSeed, params = params, parentOpt = parentOpt, mcParent = mcParent)
    (1 until count).foldLeft((Seq(firstBlock), firstBlock.mainchainHeaders.last.hash)) { (acc, i) =>
      val generatedSeq = acc._1
      val lastMc = acc._2
      val lastBlock = generatedSeq.last
      val refs = Seq(generateMainchainBlockReference(Some(byteArrayToWrapper(lastMc))))
      val newSeq: Seq[AccountBlock] = generatedSeq :+ AccountBlockFixture.copy(lastBlock,
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

  def generateNextAccountBlock(sidechainBlock: AccountBlock,
                                 companion: SidechainAccountTransactionsCompanion,
                                 params: NetworkParams,
                                 basicSeed: Long = 123177L,
                                 timestampDelta: Long = blockGenerationDelta): AccountBlock = {
    AccountBlockFixture.copy(sidechainBlock,
      parentId = sidechainBlock.id,
      timestamp = sidechainBlock.timestamp + blockGenerationDelta,
      mainchainBlocksReferencesData = Seq(),
      sidechainTransactions = Seq(),
      mainchainHeaders = Seq(),
      companion = companion,
      params = params,
      basicSeed = basicSeed)
  }

  def createSemanticallyInvalidClone(sidechainBlock: AccountBlock, companion: SidechainAccountTransactionsCompanion): AccountBlock = {
    new SemanticallyInvalidAccountBlock(sidechainBlock, companion)
  }

  // not companion should contain serializer for SemanticallyInvalidTransaction
  def generateNextAccountBlockWithInvalidTransaction(sidechainBlock: AccountBlock, companion: SidechainAccountTransactionsCompanion, params: NetworkParams, basicSeed: Long = 12325L): AccountBlock = {
    AccountBlockFixture.copy(sidechainBlock,
      parentId = sidechainBlock.id,
      timestamp = sidechainBlock.timestamp + 10,
      sidechainTransactions = Seq[SidechainTypes#SCAT](
        new SemanticallyInvalidTransaction().asInstanceOf[SidechainTypes#SCAT]),
      companion = companion,
      params = params,
      basicSeed = basicSeed)
  }

  def getRandomBlockId(seed: Long = 1312): ModifierId = {
    val id: Array[Byte] = new Array[Byte](32)
    new scala.util.Random(seed).nextBytes(id)
    bytesToId(id)
  }
}