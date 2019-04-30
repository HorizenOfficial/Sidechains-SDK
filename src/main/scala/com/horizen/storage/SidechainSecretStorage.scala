package com.horizen.storage

import java.lang.{Exception => JException}
import java.util.{ArrayList => JArrayList}

import javafx.util.{Pair => JPair}
import scorex.util.ScorexLogging

import scala.util.{Success, Failure, Try}
import scala.collection.JavaConverters._
import com.horizen.{SidechainSettings, SidechainTypes}
import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.secret._
import com.horizen.proposition._
import com.horizen.utils.ByteArrayWrapper
import scorex.crypto.hash.Blake2b256

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class SidechainSecretStorage (storage : Storage, sidechainSecretsCompanion: SidechainSecretsCompanion)
  extends ScorexLogging
{
  // Version - RandomBytes(32)
  // Key - Blake2b256 hash from public key bytes

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainSecretsCompanion != null, "SidechainSecretsCompanion must be NOT NULL.")

  private val _secrets = new mutable.LinkedHashMap[ByteArrayWrapper, Secret]()

  loadSecrets()

  private def calculateKey(proposition: ProofOfKnowledgeProposition[_ <: Secret]) : ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(proposition.bytes))
  }

  private def loadSecrets() : Unit = {
    _secrets.clear()
    for (s <- storage.getAll.asScala) {
      val secret = sidechainSecretsCompanion.parseBytes(s.getValue.data)
      if (secret.isSuccess)
        _secrets.put(calculateKey(secret.get.publicImage()), secret.get)
      else
        throw new RuntimeException("Error while secret key parsing.")
    }
  }

  def get (proposition : ProofOfKnowledgeProposition[_ <: Secret]) : Option[Secret] = {
    _secrets.get(calculateKey(proposition))
  }

  def get (propositions : List[ProofOfKnowledgeProposition[_ <: Secret]]) : List[Secret] = {
    val secretList = new ListBuffer[Secret]()

    for (p <- propositions) {
      val s = _secrets.get(calculateKey(p))
      if (s.isDefined)
        secretList.append(s.get)
    }

    secretList.toList
  }

  def getAll : List[Secret] = {
    _secrets.values.toList
  }

  def add (secret : Secret) : Try[SidechainSecretStorage] = Try {
    require(secret != null, "Secret must be NOT NULL.")
    val version = new Array[Byte](32)
    val key = calculateKey(secret.publicImage())

    require(!_secrets.contains(key), "Key already exists - " + secret)

    val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(secret))

    scala.util.Random.nextBytes(version)

    _secrets.put(key, secret)
    storage.update(new ByteArrayWrapper(version),
      List[ByteArrayWrapper]().asJava,
      List(new JPair(key, value)).asJava)

    this
  }

  def add (secretList : List[Secret]) : Try[SidechainSecretStorage] = Try {
    require(!secretList.contains(null), "Secret must be NOT NULL.")
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (s <- secretList) {
      val key = calculateKey(s.publicImage())
      require(!_secrets.contains(key), "Key already exists - " + s)
      _secrets.put(key, s)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](key,
        new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))))
    }

    storage.update(new ByteArrayWrapper(version),
      List[ByteArrayWrapper]().asJava,
      updateList)

    this
  }

  def remove (proposition : ProofOfKnowledgeProposition[_ <: Secret]) : Try[SidechainSecretStorage] = Try {
    require(proposition != null, "Proposition must be NOT NULL.")
    val version = new Array[Byte](32)
    val key = calculateKey(proposition)

    scala.util.Random.nextBytes(version)

    _secrets.remove(key)
    storage.update(new ByteArrayWrapper(version),
      List(key).asJava,
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava)

    this
  }

  def remove (propositionList : List[ProofOfKnowledgeProposition[_ <: Secret]]) : Try[SidechainSecretStorage] = Try {
    require(!propositionList.contains(null), "Proposition must be NOT NULL.")
    val removeList = new JArrayList[ByteArrayWrapper]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (p <- propositionList) {
      val key = calculateKey(p)
      _secrets.remove(key)
      removeList.add(key)
    }

    storage.update(new ByteArrayWrapper(version),
      removeList,
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava)

    this
  }

}
