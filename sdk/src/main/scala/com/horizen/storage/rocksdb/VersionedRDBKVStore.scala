package com.horizen.storage.rocksdb

import com.horizen.storage.leveldb.Algos
import com.horizen.storageVersioned.{StorageVersioned, TransactionVersioned}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.iq80.leveldb.{DB, ReadOptions}
import scorex.util.ScorexLogging

import java.util.Optional
import scala.collection.JavaConverters.{iterableAsScalaIterableConverter, mapAsJavaMapConverter, setAsJavaSetConverter}
import scala.collection.mutable
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.util.{Failure, Success, Try}


/**
  * That source code had been copied/modified from ErgoPlatform Project
  *
  * A LevelDB wrapper providing additional versioning layer along with a convenient db interface.
  */
final class VersionedRDBKVStore(protected val db: StorageVersioned, keepVersions: Int) extends RKVStore with ScorexLogging{

  import com.horizen.storage.leveldb.VersionedLDBKVStore.VersionId

  val VersionsKey: Array[Byte] = Algos.hash("versions")

  val ChangeSetPrefix: Byte = 0x16

  /**
    * Performs versioned update.
    * @param toInsert - key, value pairs to be inserted/updated
    * @param toRemove - keys to be removed
    */
  def update(toInsert: Seq[(K, V)], toRemove: Seq[K])(version: VersionId): Unit = {
    try {
      val versionIdOpt: Optional[String] = Optional.empty()

      val transaction: TransactionVersioned = db.createTransaction(versionIdOpt).asScala.getOrElse(throw new Exception("Could not create a transaction"))
      transaction.update(toInsert.toMap.asJava, toRemove.toSet.asJava)
      transaction.commit(Optional.of(BytesUtils.toHexString(version)))

    } catch {
      case e: Throwable => log.error(s"Could not update RocksDB with version ${version}", e)
    }
  }

// NOT USED
  def insert(toInsert: Seq[(K, V)])(version: VersionId): Unit = update(toInsert, Seq.empty)(version)

  def remove(toRemove: Seq[K])(version: VersionId): Unit = update(Seq.empty, toRemove)(version)

  /**
    * Rolls storage state back to the specified checkpoint.
    * @param versionId - version id to roll back to
    */
  def rollbackTo(versionId: VersionId): Try[Unit] = { Try {
      db.rollback(BytesUtils.toHexString(versionId))
    }
  }

  def versions: Seq[VersionId] = db.rollbackVersions().asScala.toSeq.map(BytesUtils.fromHexString)

  def versionIdExists(versionId: VersionId): Boolean =
    versions.exists(new ByteArrayWrapper(_) == new ByteArrayWrapper(versionId))

}


