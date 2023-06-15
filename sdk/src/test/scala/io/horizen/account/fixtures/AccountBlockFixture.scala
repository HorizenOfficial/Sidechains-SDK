package io.horizen.account.fixtures

import io.horizen.account.block.AccountBlock
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.utils.Bloom
import io.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader}
import io.horizen.consensus.ForgingStakeInfo
import io.horizen.fixtures.{CompanionsFixture, MainchainBlockReferenceFixture, MerkleTreeFixture}
import io.horizen.params.NetworkParams
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.VrfPublicKey
import io.horizen.secret.{PrivateKey25519, VrfKeyGenerator, VrfSecretKey}
import io.horizen.transaction.TransactionSerializer
import io.horizen.utils._
import io.horizen.vrf.VrfOutput
import io.horizen.{SidechainTypes, utils}
import sparkz.core.block.Block
import sparkz.util.{ModifierId, bytesToId}

import java.lang.{Byte => JByte}
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.{HashMap => JHashMap}
import scala.util.{Failure, Random, Try}


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
           vrfOutput: VrfOutput = null,
           merklePath: MerklePath = null,
           companion: SidechainAccountTransactionsCompanion,
           params: NetworkParams,
           signatureOption: Option[Signature25519] = None,
           basicSeed: Long = 0L
          ): AccountBlock = {

    //val (accountPayment, forgerMetadata) = firstOrSecond(forgerAccountData, ForgerAccountFixture.generateForgerAccount(basicSeed))
    val (_, forgerMetadata) = ForgerAccountFixture.generateForgerAccountData(basicSeed)


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
      firstOrSecond(vrfOutput, initialBlock.header.vrfOutput),
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
                           vrfProofOpt: Option[VrfProof] = None,
                           vrfOutputOpt: Option[VrfOutput] = None,
                           transactions: Option[Seq[SidechainTypes#SCAT]] = None,
                           bloom: Option[Bloom] = None,
                            ): AccountBlock = {
    assert(vrfProofOpt.isDefined == vrfOutputOpt.isDefined, "VRF proof and output must be both defined or not")
    val vrfKey = VrfKeyGenerator.getInstance().generateSecret(Array.fill(32)(basicSeed.toByte))
    val vrfMessage = "Some non random string as input".getBytes(StandardCharsets.UTF_8)
    val vrfProof = vrfProofOpt.getOrElse(vrfKey.prove(vrfMessage).getKey)
    val vrfOutput = vrfOutputOpt.getOrElse(vrfProof.proofToVrfOutput(vrfKey.publicImage(), vrfMessage).get())

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
    val baseFee: BigInteger = BigInteger.ZERO
    val gasUsed: BigInteger = BigInteger.ZERO
    val gasLimit: BigInteger = BigInteger.ZERO
    val logsBloom: Bloom = bloom.getOrElse(new Bloom())
    val sidechainTransactions = transactions.getOrElse(Seq())

    AccountBlock.create(
      parent,
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      timestamp,
      references.map(_.data),
      sidechainTransactions,
      references.map(_.header),
      Seq(),
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      vrfOutput,
      MerkleTreeFixture.generateRandomMerklePath(basicSeed),
      new Array[Byte](32),
      new Array[Byte](32),
      new Array[Byte](32),
      forgerAddress,
      baseFee,
      gasUsed,
      gasLimit,
      companion,
      logsBloom
    ).get
  }
}

trait AccountBlockFixture extends MainchainBlockReferenceFixture with AccountBlockHeaderFixture {

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
                                 basicSeed: Long = 123177L): AccountBlock = {
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

  def getRandomBlockId(seed: Long = 1312): ModifierId = {
    val id: Array[Byte] = new Array[Byte](32)
    new Random(seed).nextBytes(id)
    bytesToId(id)
  }
}
