package com.horizen.storage

import java.lang.{Exception => JException}
import java.util.{ArrayList => JArrayList}

import javafx.util.{Pair => JPair}
import scorex.util.ScorexLogging

import scala.util.{Failure, Try}
import scala.collection.JavaConverters._
import com.horizen.SidechainSettings
import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.secret._
import com.horizen.proposition._
import com.horizen.utils.ByteArrayWrapper

import scala.collection.mutable.ListBuffer

class SidechainSecretStorage(storage : Storage)
  extends ScorexLogging
{
  // Version - public key bytes
  // Key - byte array public key bytes?

  def get (proposition : Proposition) : Try[Secret] = {
    val secretBytes = storage.get(new ByteArrayWrapper(proposition.bytes))

    if (secretBytes.isPresent)
      SidechainSecretsCompanion.sidechainSecretsCompanion.parseBytes(secretBytes.get().data)
    else
      Failure(new JException("Secret key not found!"))
  }

  def get (propositions : List[Proposition]) : List[Try[Secret]] = {
    val secretList = new ListBuffer[Try[Secret]]()

    for (p <- propositions)
      secretList.append(get(p))

    secretList.toList
  }

  def getAll : List[Try[Secret]] = {
    val secretList = ListBuffer[Try[Secret]]()
    val v = storage.getAll

    for(s <- storage.getAll.asScala)
      secretList.append(SidechainSecretsCompanion.sidechainSecretsCompanion.parseBytes(s.getValue.data))

    secretList.toList
  }

  def update (secret : Secret) : Unit = {
    val key = new ByteArrayWrapper(secret.publicImage().bytes)
    val value = new ByteArrayWrapper(SidechainSecretsCompanion.sidechainSecretsCompanion.toBytes(secret))

    storage.update(key,
      List[ByteArrayWrapper]().asJava,
      List(new JPair(key, value)).asJava)
  }

  def update (secretList : List[Secret]) : Unit = {
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (s <- secretList)
      updateList.add(new JPair[ByteArrayWrapper,ByteArrayWrapper](new ByteArrayWrapper(s.publicImage().bytes),
        new ByteArrayWrapper(SidechainSecretsCompanion.sidechainSecretsCompanion.toBytes(s))))

    storage.update(new ByteArrayWrapper(version),
      List[ByteArrayWrapper]().asJava,
      updateList)
  }

  def remove (secret : Secret) : Unit = {
    val key = new ByteArrayWrapper(secret.publicImage().bytes)

    storage.update(key,
      List(key).asJava,
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava)
  }

  def remove (secretList : List[Secret]) : Unit = {
    val removeList = new JArrayList[ByteArrayWrapper]()
    val version = new Array[Byte](32)

    scala.util.Random.nextBytes(version)

    for (s <- secretList)
      removeList.add(new ByteArrayWrapper(s.publicImage().bytes))

    storage.update(new ByteArrayWrapper(version),
      removeList,
      List[JPair[ByteArrayWrapper,ByteArrayWrapper]]().asJava)

  }

}
