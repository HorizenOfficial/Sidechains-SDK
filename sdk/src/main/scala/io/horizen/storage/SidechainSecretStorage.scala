package io.horizen.storage

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.SidechainTypes
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.utils.{ByteArrayWrapper, Utils, Pair => JPair}
import sparkz.util.SparkzLogging

import java.nio.charset.StandardCharsets
import java.util.{ArrayList => JArrayList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.util.{Failure, Success, Try}

class SidechainSecretStorage(storage: Storage, sidechainSecretsCompanion: SidechainSecretsCompanion)
  extends SidechainTypes
    with SidechainStorageInfo
    with SparkzLogging
{
  // Version - RandomBytes(32)
  // Key - Blake2b256 hash from public key bytes

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainSecretsCompanion != null, "SidechainSecretsCompanion must be NOT NULL.")

  private[horizen] def getNonceKey(keyTypeSalt: Array[Byte]): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("nonce".getBytes(StandardCharsets.UTF_8), keyTypeSalt))
  }

  private val secrets = new mutable.LinkedHashMap[ByteArrayWrapper, SidechainTypes#SCS]()

  loadSecrets()

  def calculateKey(proposition: SidechainTypes#SCP): ByteArrayWrapper = Utils.calculateKey(proposition.bytes)

  private def loadSecrets(): Unit = {
    secrets.clear()

    val storageData = storage.getAll.asScala
    storageData.view
      .map(keyToSecretBytes => keyToSecretBytes.getValue.data)
      .flatMap(secretBytes => sidechainSecretsCompanion.parseBytesTry(secretBytes) match {
        case Success(secret) => Some(secret)
        case Failure(_) => None
      })
      .foreach(secret => secrets.put(calculateKey(secret.publicImage()), secret))
  }

  def get (proposition: SidechainTypes#SCP): Option[SidechainTypes#SCS] = secrets.get(calculateKey(proposition))

  def get (propositions: List[SidechainTypes#SCP]): List[SidechainTypes#SCS] = propositions.flatMap(p => secrets.get(calculateKey(p)))

  def getAll: List[SidechainTypes#SCS] = secrets.values.toList

  def add (secret: SidechainTypes#SCS): Try[SidechainSecretStorage] = Try {
    require(secret != null, "Can not add to storage: Secret must be NOT NULL.")

    val key = calculateKey(secret.publicImage())

    require(!secrets.contains(key), "Key already exists - " + secret)

    val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(secret))

    storage.update(new ByteArrayWrapper(Utils.nextVersion),
      List(new JPair(key, value)).asJava,
      List[ByteArrayWrapper]().asJava)

    secrets.put(key, secret)

    this
  }

  def add (secretList: List[SidechainTypes#SCS]): Try[SidechainSecretStorage] = Try {
    require(!secretList.contains(null), "Null secret in list: Secret must be NOT NULL.")
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    for (s <- secretList) {
      val key = calculateKey(s.publicImage())
      require(!secrets.contains(key), "Key already exists - " + s)
      secrets.put(key, s)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](key,
        new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))))
    }

    storage.update(new ByteArrayWrapper(Utils.nextVersion),
      updateList,
      List[ByteArrayWrapper]().asJava)

    this
  }

  def remove (proposition: SidechainTypes#SCP): Try[SidechainSecretStorage] = Try {
    require(proposition != null, "Proposition must be NOT NULL.")

    val key = calculateKey(proposition)

    storage.update(new ByteArrayWrapper(Utils.nextVersion),
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava,
      List(key).asJava)

    secrets.remove(key)

    this
  }

  def remove (propositionList: List[SidechainTypes#SCP]): Try[SidechainSecretStorage] = Try {
    require(!propositionList.contains(null), "Proposition must be NOT NULL.")
    val removeList = new JArrayList[ByteArrayWrapper]()

    for (p <- propositionList) {
      val key = calculateKey(p)
      secrets.remove(key)
      removeList.add(key)
    }

    storage.update(new ByteArrayWrapper(Utils.nextVersion),
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava,
      removeList)

    this
  }

  def contains(secret: SidechainTypes#SCS): Boolean = {
    require(secret != null, "Can not check if contains in storage: Secret must be NOT NULL.")
    val key = calculateKey(secret.publicImage())
    secrets.contains(key)
  }

  def isEmpty: Boolean = storage.isEmpty

  override def lastVersionId : Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def getNonce(keyTypeSalt: Array[Byte]): Option[Int] = {
    val key = getNonceKey(keyTypeSalt)
    val storageData = storage.get(key)
    storageData.asScala match {
      case Some(nonceBytes) =>
        Some(Ints.fromByteArray(nonceBytes.data))
      case _ => Option.empty
    }
  }

  def storeSecretAndNonceAtomic(secret: SidechainTypes#SCS, nonce: Int, keyTypeSalt: Array[Byte]): Try[SidechainSecretStorage] = Try  {
    require(nonce >= 0, "Nonce must be not negative")
    require(keyTypeSalt != null, "Key type salt must be NOT NULL")
    require(secret != null, "Can not add to storage: Secret must be NOT NULL.")

    val key = calculateKey(secret.publicImage())
    require(!secrets.contains(key), "Key already exists - " + secret)
    val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(secret))

    val updateList = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    val removeList = new JArrayList[ByteArrayWrapper]()

    updateList.add(new JPair(key, value))
    updateList.add(new JPair(getNonceKey(keyTypeSalt), new ByteArrayWrapper(Ints.toByteArray(nonce))))
    storage.update(new ByteArrayWrapper(Utils.nextVersion), updateList, removeList)

    secrets.put(key, secret)

    this
  }
}
