package com.horizen

import com.google.inject.Inject
import com.google.inject.name.Named
import com.horizen.backup.BoxIterator
import com.horizen.box.BoxSerializer
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.params.NetworkParams
import com.horizen.storage._
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.apache.commons.io.FileUtils
import scorex.util.ScorexLogging

import java.io._
import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import scala.util.{Failure, Success}

class SidechainBackup @Inject()
  (@Named("CustomBoxSerializers") val customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]],
   @Named("BackupStorage") val backUpStorage: Storage,
   @Named("BackUpper") val backUpper : BoxBackupInterface,
   @Named("Params") val params : NetworkParams
  ) extends ScorexLogging
  {
    protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(customBoxSerializers)
    protected val backupStorage = new BackupStorage(backUpStorage, sidechainBoxesCompanion)


    def createBackup(stateStoragePath: String, sidechainBlockIdToRollback: String, copyStateStorage: Boolean): Unit = {
      var storagePath = stateStoragePath

      if (copyStateStorage) {
        val stateStorage: File = new File(stateStoragePath)
        val stateStorageBackup: File = new File(stateStoragePath+"_copy_for_backup")

        try {
          FileUtils.copyDirectory(stateStorage, stateStorageBackup)
          storagePath = stateStoragePath+"_copy_for_backup"
        } catch {
          case t: Throwable =>
            log.error("Error during the copy of the StateStorage: ",t.getMessage)
            throw new RuntimeException("Error during the copy of the StateStorage: "+t.getMessage)
        }
      }
      val storage = new VersionedLevelDbStorageAdapter(new File(storagePath))
      val sidechainStateStorage = new SidechainStateStorage(storage, sidechainBoxesCompanion, params)
      sidechainStateStorage.rollback(new ByteArrayWrapper(BytesUtils.fromHexString(sidechainBlockIdToRollback))) match {
        case Success(stateStorage) =>
          log.info(s"Rollback of the SidechainStateStorage completed successfully!")

          //Take an iterator on the sidechainStateStorage
          val stateIterator: StorageIterator = stateStorage.getIterator
          stateIterator.seekToFirst()

          //Perform the backup in the application level
          try {
            backUpper.backup(new BoxIterator(stateIterator, sidechainBoxesCompanion), backupStorage)
            storage.close()
          } catch {
            case t: Throwable =>
              storage.close()
              log.error("Error during the Backup generation: ",t.getMessage)
              throw new RuntimeException("Error during the Backup generation: "+t.getMessage)
          }
        case Failure(e) =>
          log.info(s"Rollback of the SidechainStateStorage couldn't end successfully...", e.getMessage)
      }
    }
  }
