package io.horizen.storage.leveldb

import org.iq80.leveldb.DB

/**
  * That source code had been copied/modified from ErgoPlatform Project
  *
  * A LevelDB wrapper providing a convenient db interface.
  */
final class LDBKVStore(protected val db: DB) extends KVStore {

  def update(toInsert: Seq[(K, V)], toRemove: Seq[K]): Unit = {
    val batch = db.createWriteBatch()
    try {
      toInsert.foreach { case (k, v) => batch.put(k, v) }
      toRemove.foreach(batch.delete)
      db.write(batch)
    } finally {
      batch.close()
    }
  }

  def insert(values: Seq[(K, V)]): Unit = update(values, Seq.empty)

  def remove(keys: Seq[K]): Unit = update(Seq.empty, keys)

}
