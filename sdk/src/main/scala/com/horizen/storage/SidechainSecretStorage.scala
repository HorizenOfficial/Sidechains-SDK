package com.horizen.storage

import java.util.{ArrayList => JArrayList}

import com.horizen.SidechainTypes
import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class SidechainSecretStorage(storage: Storage, sidechainSecretsCompanion: SidechainSecretsCompanion)
  extends SidechainTypes
  with ScorexLogging
{
  // Version - RandomBytes(32)
  // Key - Blake2b256 hash from public key bytes

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainSecretsCompanion != null, "SidechainSecretsCompanion must be NOT NULL.")

  private val secrets = new mutable.LinkedHashMap[ByteArrayWrapper, SidechainTypes#SCS]()

  loadSecrets()

  def calculateKey(proposition: SidechainTypes#SCP): ByteArrayWrapper = new ByteArrayWrapper(Blake2b256.hash(proposition.bytes))

  private def loadSecrets(): Unit = {
    secrets.clear()

    val storageData = storage.getAll.asScala
    storageData.view
      .map(keyToSecretBytes => keyToSecretBytes.getValue.data)
      .map(secretBytes => sidechainSecretsCompanion.parseBytes(secretBytes))
      .foreach(secret => secrets.put(calculateKey(secret.publicImage()), secret))
  }

  def get (proposition: SidechainTypes#SCP): Option[SidechainTypes#SCS] = secrets.get(calculateKey(proposition))

  def get (propositions: List[SidechainTypes#SCP]): List[SidechainTypes#SCS] = propositions.flatMap(p => secrets.get(calculateKey(p)))

  def getAll: List[SidechainTypes#SCS] = secrets.values.toList

  def add (secret: SidechainTypes#SCS): Try[SidechainSecretStorage] = Try {
    require(secret != null, "Secret must be NOT NULL.")
    val version = new Array[Byte](32)
    val key = calculateKey(secret.publicImage())

    require(!secrets.contains(key), "Key already exists - " + secret)

    val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(secret))

    scala.util.Random.nextBytes(version)

    storage.update(new ByteArrayWrapper(version),
      List(new JPair(key, value)).asJava,
      List[ByteArrayWrapper]().asJava)

    secrets.put(key, secret)

    this
  }

  def add (secretList: List[SidechainTypes#SCS]): Try[SidechainSecretStorage] = Try {
    require(!secretList.contains(null), "Secret must be NOT NULL.")
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (s <- secretList) {
      val key = calculateKey(s.publicImage())
      require(!secrets.contains(key), "Key already exists - " + s)
      secrets.put(key, s)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](key,
        new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))))
    }

    storage.update(new ByteArrayWrapper(version),
      updateList,
      List[ByteArrayWrapper]().asJava)

    this
  }

  def remove (proposition: SidechainTypes#SCP): Try[SidechainSecretStorage] = Try {
    require(proposition != null, "Proposition must be NOT NULL.")
    val version = new Array[Byte](32)
    val key = calculateKey(proposition)

    scala.util.Random.nextBytes(version)

    storage.update(new ByteArrayWrapper(version),
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava,
      List(key).asJava)

    secrets.remove(key)

    this
  }

  def remove (propositionList: List[SidechainTypes#SCP]): Try[SidechainSecretStorage] = Try {
    require(!propositionList.contains(null), "Proposition must be NOT NULL.")
    val removeList = new JArrayList[ByteArrayWrapper]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (p <- propositionList) {
      val key = calculateKey(p)
      secrets.remove(key)
      removeList.add(key)
    }

    storage.update(new ByteArrayWrapper(version),
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava,
      removeList)

    this
  }

  def isEmpty: Boolean = storage.isEmpty

}
