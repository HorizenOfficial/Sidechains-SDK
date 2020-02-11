package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.{BoxMerklePathInfo, BoxMerklePathInfoSerializer, ByteArrayWrapper, ListSerializer, Pair}
import scorex.util.ScorexLogging
import scorex.crypto.hash.Blake2b256

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Random, Success, Try}
import java.util.{ArrayList => JArrayList}


class ForgingBoxesMerklePathStorage(storage: Storage) extends SidechainTypes with ScorexLogging
{
  require(storage != null, "Storage must be NOT NULL.")

  private[horizen] def epochKey(epoch: ConsensusEpochNumber): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256(s"epoch$epoch"))
  private[horizen] val maxNumberOfStoredEpochs: Int = 3

  private[horizen] val boxMerklePathInfoListSerializer = new ListSerializer[BoxMerklePathInfo](BoxMerklePathInfoSerializer)

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
  def update(epoch: ConsensusEpochNumber, boxMerklePathInfoSeq: Seq[BoxMerklePathInfo]): Try[ForgingBoxesMerklePathStorage] = Try {
    require(boxMerklePathInfoSeq != null, "Seq of boxMerklePathInfoSeq to append must be NOT NULL. Use empty Seq instead.")

    // remove data of the epoch with number (epoch - maxNumberOfStoredEpochs) if exists.
    val removeList = new JArrayList[ByteArrayWrapper]()
    removeList.add(epochKey(ConsensusEpochNumber @@ (epoch - maxNumberOfStoredEpochs)))

    val updateList = new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    updateList.add(new Pair(epochKey(epoch), new ByteArrayWrapper(boxMerklePathInfoListSerializer.toBytes(boxMerklePathInfoSeq.asJava))))

    storage.update(nextVersion, updateList, removeList)

    this
  }

  // When new block applied we anchor the new version to its id.
  // This version can be used as a rollback point during rollback process.
  def updateVersion(version: ByteArrayWrapper): Try[ForgingBoxesMerklePathStorage] = Try {
    storage.update(version, new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]](), new JArrayList[ByteArrayWrapper]())
    this
  }

  def getMerklePathsForEpoch(epoch: ConsensusEpochNumber): Option[Seq[BoxMerklePathInfo]] = {
    storage.get(epochKey(epoch)).asScala match {
      case Some(baw) =>
        boxMerklePathInfoListSerializer.parseBytesTry(baw.data) match {
          case Success(boxMerklePathsInfo) => Some(boxMerklePathsInfo.asScala)
          case Failure(exception) =>
            log.error("Error while box merkle paths info parsing.", exception)
            None
        }
      case _ => None
    }
  }

  def lastVersionId: Option[ByteArrayWrapper] = {
    val lastVersion = storage.lastVersionID()
    if (lastVersion.isPresent)
      Some(lastVersion.get())
    else
      None
  }

  def rollbackVersions: List[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback(version: ByteArrayWrapper): Try[ForgingBoxesMerklePathStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}
