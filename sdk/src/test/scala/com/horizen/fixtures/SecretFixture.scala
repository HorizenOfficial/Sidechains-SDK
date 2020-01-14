package com.horizen.fixtures

import com.horizen.secret._
import com.horizen.customtypes._
import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.vrf.VRFPublicKey

import scala.util.Random

trait SecretFixture {
  val pkc = PrivateKey25519Creator.getInstance()

  val pk1 = pkc.generateSecret("seed1".getBytes())
  val pk2 = pkc.generateSecret("seed2".getBytes())
  val pk3 = pkc.generateSecret("seed3".getBytes())
  val pk4 = pkc.generateSecret("seed4".getBytes())
  val pk5 = pkc.generateSecret("seed5".getBytes())
  val pk6 = pkc.generateSecret("seed6".getBytes())

  val pk7 = pkc.generateSecret("seed7".getBytes())

  def getPrivateKey25519: PrivateKey25519 = {
    val seed = new Array[Byte](32)
    Random.nextBytes(seed)
    pkc.generateSecret(seed)
  }

  def getPrivateKey25519(seed: Array[Byte]): PrivateKey25519 = {
    pkc.generateSecret(seed)
  }

  def getPrivateKey25519List(count: Int): JList[PrivateKey25519] = {
    val seed = new Array[Byte](32)
    val keysList : JList[PrivateKey25519] = new JArrayList()
    for (i <- 1 to count) {
      Random.nextBytes(seed)
      keysList.add(pkc.generateSecret(seed))
    }
    keysList
  }

  def getCustomPrivateKey: CustomPrivateKey = {
    val privateBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)

    Random.nextBytes(privateBytes)
    Random.nextBytes(publicBytes)

    new CustomPrivateKey(privateBytes, publicBytes)
  }

  def getCustomPrivateKeyList(count: Int): JList[CustomPrivateKey] = {
    val privateBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)
    val keysList: JList[CustomPrivateKey] = new JArrayList()

    for (i <- 1 to count) {
      Random.nextBytes(privateBytes)
      Random.nextBytes(publicBytes)

      keysList.add(new CustomPrivateKey(privateBytes, publicBytes))
    }

    keysList
  }

  def getMCPublicKeyHashProposition: MCPublicKeyHashProposition = {
    val keyHashBytes = new Array[Byte](MCPublicKeyHashProposition.KEY_LENGTH)
    Random.nextBytes(keyHashBytes)

    new MCPublicKeyHashProposition(keyHashBytes)
  }

  def getMCPublicKeyHashPropositionList(count: Int): JList[MCPublicKeyHashProposition] = {
    val keyList = new JArrayList[MCPublicKeyHashProposition]()

    for (i <- 1 to count)
      keyList.add(getMCPublicKeyHashProposition)

    keyList
  }

  def getVRFPublicKey: VRFPublicKey = {
    val keyHashBytes = new Array[Byte](VRFPublicKey.length)
    Random.nextBytes(keyHashBytes)

    new VRFPublicKey(keyHashBytes)
  }

  def getVRFPublicKey(seed: Long): VRFPublicKey = {
    Random.setSeed(seed)
    getVRFPublicKey
  }
}

class SecretFixtureClass extends SecretFixture