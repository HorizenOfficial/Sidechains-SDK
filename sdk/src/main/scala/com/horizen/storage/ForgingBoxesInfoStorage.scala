package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.{ByteArrayWrapper, ForgingStakeMerklePathInfo, ForgerBoxMerklePathInfoSerializer, ListSerializer, Pair}
import scorex.util.ScorexLogging
import scorex.crypto.hash.Blake2b256

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Random, Success, Try}
import java.util.{ArrayList => JArrayList}

import com.horizen.box.{ForgerBox, ForgerBoxSerializer}


class ForgingBoxesInfoStorage(storage: Storage) extends SidechainTypes with ScorexLogging
{
  require(storage != null, "Storage must be NOT NULL.")

  private[horizen] def epochKey(epoch: ConsensusEpochNumber): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"epoch$epoch"))
  private[horizen] val forgerBoxesKey: ByteArrayWrapper = new ByteArrayWrapper(Blake2b256.hash("forgerBoxesKey".getBytes()))
  private[horizen] val maxNumberOfStoredEpochs: Int = 3

  private[horizen] val forgingStakeMerklePathInfoListSerializer = new ListSerializer[ForgingStakeMerklePathInfo](ForgerBoxMerklePathInfoSerializer)
  private[horizen] val forgerBoxListSerializer = new ListSerializer[ForgerBox](ForgerBoxSerializer.getSerializer)

  private def nextVersion: ByteArrayWrapper = {
    val version = new Array[Byte](32)
    lastVersionId match {
      case Some(lastVersion) => new Random(lastVersion.hashCode()).nextBytes(version)
      case None => Random.nextBytes(version)
    }

    new ByteArrayWrapper(version)
  }

  // Current storage is updated with consensus epoch info between block modifiers application.
  // The random version here is not used as a point to rollback.
  def updateForgingStakeMerklePathInfo(epoch: ConsensusEpochNumber, boxMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo]): Try[ForgingBoxesInfoStorage] = Try {
    require(boxMerklePathInfoSeq != null, "Seq of boxMerklePathInfoSeq to append must be NOT NULL. Use empty Seq instead.")

    // remove data of the epoch with number (epoch - maxNumberOfStoredEpochs) if exists.
    val removeList = new JArrayList[ByteArrayWrapper]()
    removeList.add(epochKey(ConsensusEpochNumber @@ (epoch - maxNumberOfStoredEpochs)))

    val updateList = new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    updateList.add(new Pair(epochKey(epoch), new ByteArrayWrapper(forgingStakeMerklePathInfoListSerializer.toBytes(boxMerklePathInfoSeq.asJava))))

    storage.update(nextVersion, updateList, removeList)

    this
  }

  // When new block applied we anchor the new version to its id.
  // This version can be used as a rollback point during rollback process.
  def updateForgerBoxes(version: ByteArrayWrapper,
                        forgerBoxesToAppendSeq: Seq[ForgerBox],
                        boxIdsRemoveSeq: Seq[Array[Byte]]): Try[ForgingBoxesInfoStorage] = Try {
    val toUpdate: JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]] = new JArrayList()

    val currentForgerBoxSeq: Seq[ForgerBox] = getForgerBoxes.getOrElse(Seq())
    val existentForgerBoxSeq: Seq[ForgerBox] = currentForgerBoxSeq.filterNot(box => boxIdsRemoveSeq.exists(removedId => box.id().sameElements(removedId)))
    val newForgerBoxSeq = existentForgerBoxSeq ++ forgerBoxesToAppendSeq

    // Update only if current ForgerBox sequence was changed: some boxes were removed and/or some boxes were added.
    if(existentForgerBoxSeq.size != currentForgerBoxSeq.size || forgerBoxesToAppendSeq.nonEmpty)
      toUpdate.add(new Pair(forgerBoxesKey, new ByteArrayWrapper(forgerBoxListSerializer.toBytes(newForgerBoxSeq.asJava))))

    storage.update(version, toUpdate, new JArrayList())
    this
  }

  def getForgerBoxes: Option[Seq[ForgerBox]] = {
    storage.get(forgerBoxesKey).asScala match {
      case Some(baw) =>
        forgerBoxListSerializer.parseBytesTry(baw.data) match {
          case Success(forgerBoxes) => Some(forgerBoxes.asScala)
          case Failure(exception) =>
            log.error("Error while forger boxes list parsing.", exception)
            None
        }
      case _ => None
    }
  }

  def getForgingStakeMerklePathInfoForEpoch(epoch: ConsensusEpochNumber): Option[Seq[ForgingStakeMerklePathInfo]] = {
    storage.get(epochKey(epoch)).asScala match {
      case Some(baw) =>
        forgingStakeMerklePathInfoListSerializer.parseBytesTry(baw.data) match {
          case Success(boxMerklePathsInfo) => Some(boxMerklePathsInfo.asScala)
          case Failure(exception) =>
            log.error("Error while box merkle paths info parsing.", exception)
            None
        }
      case _ => None
    }
  }

  def lastVersionId: Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions: List[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback(version: ByteArrayWrapper): Try[ForgingBoxesInfoStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}
