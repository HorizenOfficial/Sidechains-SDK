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

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class SidechainSecretStorage(storage : Storage, sidechainSecretsCompanion: SidechainSecretsCompanion)
  extends ScorexLogging
{
  // Version - public key bytes
  // Key - byte array public key bytes?

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainSecretsCompanion != null, "SidechainSecretsCompanion must be NOT NULL.")

  private val _secrets = new mutable.LinkedHashMap[Proposition, Secret]()

  private def loadSecrets : Unit = {
    _secrets.clear()
    for (s <- storage.getAll.asScala) {
      val secret = sidechainSecretsCompanion.parseBytes(s.getValue.data)
      if (secret.isSuccess)
        _secrets.put(secret.get.publicImage().asInstanceOf[Proposition], secret.get)
      else
        log.error("Error while secret key parsing.", secret)
    }
  }

  def get (proposition : Proposition) : Option[Secret] = {
    _secrets.get(proposition)
  }

  def get (propositions : List[Proposition]) : List[Secret] = {
    val secretList = new ListBuffer[Secret]()

    for (p <- propositions) {
      val s = _secrets.get(p)
      if (s.isDefined)
        secretList.append(s.get)
    }

    secretList.toList
  }

  def getAll : List[Secret] = {
    _secrets.values.toList
  }

  def add (secret : Secret) : Unit = {
    val version = new Array[Byte](32)
    val key = new ByteArrayWrapper(secret.publicImage().bytes)
    val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(secret))

    scala.util.Random.nextBytes(version)

    _secrets.put(secret.publicImage().asInstanceOf[Proposition], secret)
    storage.update(new ByteArrayWrapper(version),
      List[ByteArrayWrapper]().asJava,
      List(new JPair(key, value)).asJava)
  }

  def add (secretList : List[Secret]) : Unit = {
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (s <- secretList) {
      _secrets.put(s.publicImage().asInstanceOf[Proposition], s)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper(s.publicImage().bytes),
        new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))))
    }

    storage.update(new ByteArrayWrapper(version),
      List[ByteArrayWrapper]().asJava,
      updateList)
  }

  def remove (proposition : Proposition) : Unit = {
    val version = new Array[Byte](32)
    val key = new ByteArrayWrapper(proposition.bytes)

    scala.util.Random.nextBytes(version)

    _secrets.remove(proposition)
    storage.update(new ByteArrayWrapper(version),
      List(key).asJava,
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava)
  }

  def remove (propositionList : List[Proposition]) : Unit = {
    val removeList = new JArrayList[ByteArrayWrapper]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (p <- propositionList) {
      _secrets.remove(p)
      removeList.add(new ByteArrayWrapper(p.bytes))
    }

    storage.update(new ByteArrayWrapper(version),
      removeList,
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava)

  }

}
