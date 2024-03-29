package io.horizen.storage

import io.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.{AbstractFeePaymentsInfo, ActiveChain, MainchainBlockReferenceDataInfo, MainchainBlockReferenceInfo, MainchainHeaderBaseInfo, MainchainHeaderHash, MainchainHeaderInfo, MainchainHeaderMetadata, SidechainBlockInfo, SidechainBlockInfoSerializer, byteArrayToMainchainHeaderHash}
import io.horizen.params.NetworkParams
import io.horizen.utils.ByteArrayWrapper
import sparkz.core.consensus.ModifierSemanticValidity
import io.horizen.transaction.Transaction
import sparkz.crypto.hash.Blake2b256
import sparkz.util.{ModifierId, SparkzLogging, bytesToId, idToBytes}

import scala.collection.mutable.ArrayBuffer
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}
import io.horizen.utils._

import java.util.{ArrayList => JArrayList, List => JList}
import io.horizen.utils.{Pair => JPair}
import sparkz.core.serialization.SparkzSerializer

trait SidechainBlockInfoProvider {
  def blockInfoById(blockId: ModifierId): SidechainBlockInfo
}

abstract class AbstractHistoryStorage[
  PM <: SidechainBlockBase[_ <: Transaction, _ <: SidechainBlockHeaderBase],
  FPI <: AbstractFeePaymentsInfo,
  S <: AbstractHistoryStorage[PM, FPI, S]
  ]  (
    storage: Storage,
    blockSerializer: SparkzSerializer[PM],
    feePaymentsInfoSerializer: SparkzSerializer[FPI],
    params: NetworkParams
  )
  extends SidechainBlockInfoProvider
    with SidechainStorageInfo
    with SparkzLogging {
  this: S =>
  // Version - RandomBytes(32)

  require(storage != null, "Storage must be NOT NULL.")
  require(blockSerializer != null, "Block serializer must be NOT NULL.")
  require(params != null, "params must be NOT NULL.")

  private val bestBlockIdKey: ByteArrayWrapper = new ByteArrayWrapper(Array.fill(32)(-1: Byte))

  private val activeChain: ActiveChain = loadActiveChain()

  private def loadActiveChain(): ActiveChain = {
    if (storage.isEmpty) {
      return ActiveChain(params.mainchainCreationBlockHeight)
    }

    val activeChainBlocksInfo: ArrayBuffer[(ModifierId, SidechainBlockInfo)] = new ArrayBuffer()

    activeChainBlocksInfo.append((bestBlockId, blockInfoByIdFromStorage(bestBlockId)))
    while (activeChainBlocksInfo.last._2.height > 1) {
      val id = activeChainBlocksInfo.last._2.parentId
      activeChainBlocksInfo.append((id, blockInfoByIdFromStorage(id)))
    }

    val orderedChainBlocks = activeChainBlocksInfo.reverse

    val mainchainBlockParent = for {
      firstSidechainBlockInfo <- orderedChainBlocks.headOption
      firstSidechainBlock <- blockById(firstSidechainBlockInfo._1)
      firstMainchainHeader <- firstSidechainBlock.mainchainHeaders.headOption
    } yield byteArrayToMainchainHeaderHash(firstMainchainHeader.hashPrevBlock)

    ActiveChain(orderedChainBlocks, mainchainBlockParent.getOrElse(throw new IllegalStateException("Loaded active chain miss mainchain parent")), params.mainchainCreationBlockHeight)
  }

  private def blockInfoKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"blockInfo$blockId"))

  protected def feePaymentsInfoKey(blockId: ModifierId): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"feePaymentsInfo$blockId"))

  def height: Int = activeChain.height

  def heightOf(blockId: ModifierId): Option[Int] = {
    blockInfoOptionById(blockId).map(_.height)
  }

  def bestBlockId: ModifierId = storage.get(bestBlockIdKey).asScala.map(d => bytesToId(d.data)).getOrElse(params.sidechainGenesisBlockId)

  def bestBlock: PM = {
    require(height > 0, "SidechainHistoryStorage is empty. Cannot retrieve best block.")
    blockById(bestBlockId).get
  }

  def bestBlockInfo: SidechainBlockInfo = {
    require(height > 0, "SidechainHistoryStorage is empty. Cannot retrieve best block.")
    blockInfoById(bestBlockId)
  }

  def blockById(blockId: ModifierId): Option[PM] = {
    val blockIdBytes = new ByteArrayWrapper(idToBytes(blockId))
    val baw = storage.get(blockIdBytes).asScala
    baw match {
      case Some(value) =>
        blockSerializer.parseBytesTry(value.data()) match {
          case Success(block) => Option(block)
          case Failure(exception) =>
            log.error("Error while sidechain block parsing.", exception)
            Option.empty
        }
      case None =>
        log.info("SidechainHistoryStorage:blockById: byte array is empty")
        None
    }
  }

  //Block info shall be in history storage, otherwise something going totally wrong
  def blockInfoById(blockId: ModifierId): SidechainBlockInfo = {
    blockInfoOptionById(blockId).getOrElse(throw new IllegalStateException(s"No block info for block $blockId"))
  }

  def blockInfoOptionById(blockId: ModifierId): Option[SidechainBlockInfo] = {
    activeChain.blockInfoById(blockId).orElse(blockInfoOptionByIdFromStorage(blockId))
  }

  def contains(blockId: ModifierId): Boolean = {
    isInActiveChain(blockId) || blockInfoOptionByIdFromStorage(blockId).nonEmpty
  }

  private def blockInfoOptionByIdFromStorage(blockId: ModifierId): Option[SidechainBlockInfo] = {
    storage.get(blockInfoKey(blockId)).asScala.flatMap(baw => SidechainBlockInfoSerializer.parseBytesTry(baw.data).toOption)
  }

  private def blockInfoByIdFromStorage(blockId: ModifierId): SidechainBlockInfo = {
    blockInfoOptionByIdFromStorage(blockId).getOrElse(throw new IllegalArgumentException(s"No blockInfo in storage for blockId $blockId"))
  }

  def getLastMainchainHeaderBaseInfoInclusion(blockId: ModifierId): MainchainHeaderBaseInfo = {
    var sidechainBlockInfo: SidechainBlockInfo = this.blockInfoById(blockId)
    while(sidechainBlockInfo.mainchainHeaderBaseInfo.isEmpty) {
      sidechainBlockInfo = this.blockInfoById(sidechainBlockInfo.parentId)
    }

    sidechainBlockInfo.mainchainHeaderBaseInfo.last
  }

  def tooManyBlocksWithoutMcHeadersDataSince(blockId: ModifierId): Boolean = {
    var sidechainBlockInfo: SidechainBlockInfo = this.blockInfoById(blockId)
    var scBlocksCount : Int = 0
    var blockHasMcHeaders : Boolean = sidechainBlockInfo.mainchainHeaderBaseInfo.nonEmpty

    // go backwards on the chain until we found a mc header or we hit the genesis block, and break if we
    // cross the threshold value of max revertable number of blocks, included the latest block containing mc refs
    // In case a MC reorg, all SC blocks would be reverted including the one referencing the MC block
    val maxBlocksWithoutMcHeaders = params.maxHistoryRewritingLength - 1

    while(!blockHasMcHeaders) {
      scBlocksCount = scBlocksCount + 1

      if (scBlocksCount >= maxBlocksWithoutMcHeaders ) {
        log.warn(s"Unexpectedly large number of consecutive SC blocks with no mc block references")
        return true
      }

      if (sidechainBlockInfo.parentId == params.sidechainGenesisBlockId)
        return false

      sidechainBlockInfo = this.blockInfoById(sidechainBlockInfo.parentId)
      blockHasMcHeaders = sidechainBlockInfo.mainchainHeaderBaseInfo.nonEmpty
      if (blockHasMcHeaders)
        log.debug(s"found sc block ${sidechainBlockInfo.parentId} which has ${sidechainBlockInfo.mainchainHeaderBaseInfo.size} mc block headers")
    }
    // ok, we found a block with mc headers (or genesis block) before the limit
    false
  }

  def parentBlockId(blockId: ModifierId): Option[ModifierId] = blockInfoOptionById(blockId).map(_.parentId)

  def chainScoreFor(blockId: ModifierId): Option[Long] = blockInfoOptionById(blockId).map(_.score)

  def isInActiveChain(blockId: ModifierId): Boolean = activeChain.contains(blockId)

  def activeChainBlockId(height: Int): Option[ModifierId] = activeChain.idByHeight(height)

  def activeChainSince(blockId: ModifierId, limit: Option[Int]): Seq[ModifierId] = activeChain.chainSince(blockId, limit)

  def activeChainAfter(blockId: ModifierId, limit: Option[Int]): Seq[ModifierId] = activeChain.chainAfter(blockId, limit)

  def getSidechainBlockContainingMainchainHeader(mainchainHeaderHash: Array[Byte]): Option[PM] = {
    activeChain.idByMcHeader(byteArrayToMainchainHeaderHash(mainchainHeaderHash)).flatMap(blockById)
  }

  def getSidechainBlockContainingMainchainReferenceData(mainchainHeaderHash: Array[Byte]): Option[PM] = {
    activeChain.idByMcReferenceData(byteArrayToMainchainHeaderHash(mainchainHeaderHash)).flatMap(blockById)
  }

  def getMainchainBlockReferenceByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReference] = {
    for {
      header <- getMainchainHeaderByHash(mainchainHeaderHash)
      data <- getMainchainReferenceDataByHash(mainchainHeaderHash)
    } yield MainchainBlockReference(header, data)
  }

  def getMainchainHeaderByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainHeader] = {
    val block: Option[PM] = getSidechainBlockContainingMainchainHeader(mainchainHeaderHash)
    block.flatMap(_.mainchainHeaders.find(header => mainchainHeaderHash.sameElements(header.hash)))
  }

  def getMainchainReferenceDataByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReferenceData] = {
    val block: Option[PM] = getSidechainBlockContainingMainchainReferenceData(mainchainHeaderHash)
    block.flatMap(_.mainchainBlockReferencesData.find(data => mainchainHeaderHash.sameElements(data.headerHash)))
  }

  def getMainchainBlockReferenceInfoByMainchainBlockHeight(mainchainHeight: Int): Option[MainchainBlockReferenceInfo] = {
    activeChain.mcHashByMcHeight(mainchainHeight).flatMap(hash => getMainchainBlockReferenceInfoByHash(hash))
  }

  def getBestMainchainBlockReferenceInfo: Option[MainchainBlockReferenceInfo] = {
    getMainchainBlockReferenceInfoByMainchainBlockHeight(activeChain.heightOfMcReferencesData)
  }

  def getMainchainBlockReferenceInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReferenceInfo] = {
    val mcHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(mainchainHeaderHash)
    for {
      mcHeight <- activeChain.mcRefDataHeightByMcHash(mcHash)
      headerContainingId <- activeChain.idByMcHeader(mcHash)
      dataContainingId <- activeChain.idByMcReferenceData(mcHash)
      mcMetadata <- activeChain.mcHeaderMetadataByMcHash(mcHash)
    } yield buildMainchainBlockReferenceInfo(mcHash, mcMetadata, mcHeight, headerContainingId, dataContainingId)
  }

  private def buildMainchainBlockReferenceInfo(mcHash: MainchainHeaderHash,
                                               referenceInfo: MainchainHeaderMetadata,
                                               mcBlockHeight: Int,
                                               mainchainHeaderSidechainBlockId: ModifierId,
                                               mainchainReferenceDataSidechainBlockId: ModifierId): MainchainBlockReferenceInfo = {
    new MainchainBlockReferenceInfo(mcHash, referenceInfo.getParentId, mcBlockHeight, idToBytes(mainchainHeaderSidechainBlockId), idToBytes(mainchainReferenceDataSidechainBlockId))
  }

  def getMainchainHashesForIndexes(mainchainHeights: Seq[Int]): Seq[MainchainHeaderHash] = {
    mainchainHeights.flatMap(mainchainHeight => activeChain.mcHashByMcHeight(mainchainHeight))
  }

  def getBestMainchainHeaderInfo: Option[MainchainHeaderInfo] = {
    getMainchainHeaderInfoByHeight(activeChain.heightOfMcHeaders)
  }

  def getMainchainHeaderInfoByHeight(mainchainHeight: Int): Option[MainchainHeaderInfo] = {
    for {
      mcHash <- activeChain.mcHashByMcHeight(mainchainHeight)
    } yield getMainchainHeaderInfoByHash(mcHash).get
  }

  def getMainchainHeaderInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainHeaderInfo] = {
    val mcHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(mainchainHeaderHash)
    for {
      mcHeight <- activeChain.mcHeadersHeightByMcHash(mcHash)
      sidechainBlockId <- activeChain.idByMcHeader(mcHash)
      mcMetadata <- activeChain.mcHeaderMetadataByMcHash(mcHash)
      blockInfo <- activeChain.blockInfoById(sidechainBlockId)
      mainchainBaseInfo <- blockInfo.mainchainHeaderBaseInfo.find(info => info.hash.equals(mcHash))
    } yield MainchainHeaderInfo(mcHash, mcMetadata.getParentId, mcHeight, sidechainBlockId, mainchainBaseInfo.cumulativeCommTreeHash)
  }

  def getBestMainchainBlockReferenceDataInfo: Option[MainchainBlockReferenceDataInfo] = {
    getMainchainBlockReferenceDataInfoByHeight(activeChain.heightOfMcReferencesData)
  }

  def getMainchainBlockReferenceDataInfoByHeight(mainchainHeight: Int): Option[MainchainBlockReferenceDataInfo] = {
    for {
      mcHash <- activeChain.mcHashByMcHeight(mainchainHeight)
    } yield getMainchainBlockReferenceDataInfoByHash(mcHash).get
  }

  def getMainchainBlockReferenceDataInfoByHash(mainchainHeaderHash: Array[Byte]): Option[MainchainBlockReferenceDataInfo] = {
    val mcHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(mainchainHeaderHash)
    for {
      mcHeight <- activeChain.mcRefDataHeightByMcHash(mcHash)
      sidechainBlockId <- activeChain.idByMcReferenceData(mcHash)
    } yield MainchainBlockReferenceDataInfo(mcHash, mcHeight, sidechainBlockId)
  }

  def update(block: PM, blockInfo: SidechainBlockInfo): Try[S] = Try {
    require(block != null, "Block must be NOT NULL.")
    require(block.parentId == blockInfo.parentId, "Passed BlockInfo data conflicts to passed Block.")

    val toUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = new JArrayList()

    // add short block info
    toUpdate.add(new JPair(new ByteArrayWrapper(blockInfoKey(block.id)), new ByteArrayWrapper(blockInfo.bytes)))

    // add block
    toUpdate.add(new JPair(new ByteArrayWrapper(idToBytes(block.id)), new ByteArrayWrapper(block.bytes)))

    storage.update(
      new ByteArrayWrapper(Utils.nextVersion),
      toUpdate,
      new JArrayList[ByteArrayWrapper]())

    this
  }

  def updateFeePaymentsInfo(blockId: ModifierId, feePaymentsInfo: FPI): Try[S] = Try {
    storage.update(
      new ByteArrayWrapper(Utils.nextVersion),
      java.util.Arrays.asList(new JPair(new ByteArrayWrapper(feePaymentsInfoKey(blockId)), new ByteArrayWrapper(feePaymentsInfo.bytes))),
      new JArrayList[ByteArrayWrapper]()
    )
    this
  }

  def getFeePaymentsInfo(blockId: ModifierId): Option[FPI] =
    storage.get(feePaymentsInfoKey(blockId)).asScala.flatMap(baw => feePaymentsInfoSerializer.parseBytesTry(baw.data).toOption)

  def semanticValidity(blockId: ModifierId): ModifierSemanticValidity = {
    blockInfoOptionById(blockId) match {
      case Some(info) => info.semanticValidity
      case None => ModifierSemanticValidity.Absent
    }
  }

  def updateSemanticValidity(block: PM, status: ModifierSemanticValidity): Try[S] = Try {
    // if it's not a part of active chain, retrieve previous info from disk storage
    val oldInfo: SidechainBlockInfo = activeChain.blockInfoById(block.id).getOrElse(blockInfoById(block.id))
    val blockInfo = oldInfo.copy(semanticValidity = status)

    storage.update(
      new ByteArrayWrapper(Utils.nextVersion),
      java.util.Arrays.asList(new JPair(new ByteArrayWrapper(blockInfoKey(block.id)), new ByteArrayWrapper(blockInfo.bytes))),
      new JArrayList()
    )
    this
  }

  def setAsBestBlock(block: PM, blockInfo: SidechainBlockInfo): Try[S] = Try {
    storage.update(
      new ByteArrayWrapper(Utils.nextVersion),
      java.util.Arrays.asList(new JPair(bestBlockIdKey, new ByteArrayWrapper(idToBytes(block.id)))),
      new JArrayList()
    )

    val mainchainParent: Option[MainchainHeaderHash] = block.mainchainHeaders.headOption.map(header => byteArrayToMainchainHeaderHash(header.hashPrevBlock))
    activeChain.setBestBlock(block.id, blockInfo, mainchainParent)
    this
  }

  def isEmpty: Boolean = storage.isEmpty

  override def lastVersionId : Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }
}
