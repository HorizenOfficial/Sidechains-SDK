package com.horizen.fixtures

import com.horizen.secret._
import com.horizen.customtypes._
import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.proof.Signature25519
import com.horizen.proposition.{MCPublicKeyHashProposition, VrfPublicKey}

import scala.util.Random

trait SecretFixture {
  val pkc: PrivateKey25519Creator = PrivateKey25519Creator.getInstance()

  val pk1: PrivateKey25519 = pkc.generateSecret("seed1".getBytes())
  val pk2: PrivateKey25519 = pkc.generateSecret("seed2".getBytes())
  val pk3: PrivateKey25519 = pkc.generateSecret("seed3".getBytes())
  val pk4: PrivateKey25519 = pkc.generateSecret("seed4".getBytes())
  val pk5: PrivateKey25519 = pkc.generateSecret("seed5".getBytes())
  val pk6: PrivateKey25519 = pkc.generateSecret("seed6".getBytes())

  val pk7: PrivateKey25519 = pkc.generateSecret("seed7".getBytes())

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

  def getRandomCustomProof: CustomProof = {
    new CustomProof(Random.nextInt())
  }

  def getRandomSignature25519: Signature25519 = {
    val pk = getPrivateKey25519
    val message = "12345".getBytes
    pk.sign(message)
  }

  def getCustomPrivateKey: CustomPrivateKey = {
    val privateBytes = new Array[Byte](CustomPrivateKey.PRIVATE_KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.PUBLIC_KEY_LENGTH)

    Random.nextBytes(privateBytes)
    Random.nextBytes(publicBytes)

    new CustomPrivateKey(privateBytes, publicBytes)
  }

  def getCustomPrivateKeyList(count: Int): JList[CustomPrivateKey] = {
    val privateBytes = new Array[Byte](CustomPrivateKey.PRIVATE_KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.PUBLIC_KEY_LENGTH)
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

  def getMcReturnAddress: Array[Byte] = {
    val address = new Array[Byte](MCPublicKeyHashProposition.KEY_LENGTH)
    Random.nextBytes(address)

    address
  }

  def getMCPublicKeyHashPropositionList(count: Int): JList[MCPublicKeyHashProposition] = {
    val keyList = new JArrayList[MCPublicKeyHashProposition]()

    for (i <- 1 to count)
      keyList.add(getMCPublicKeyHashProposition)

    keyList
  }

  def getVRFPublicKey: VrfPublicKey = {
    VrfKeyGenerator.getInstance().generateSecret(Random.nextString(32).getBytes).publicImage()
  }

  def getVRFPublicKey(seed: Long): VrfPublicKey = {
    VrfKeyGenerator.getInstance().generateSecret(seed.toString.getBytes).publicImage()
  }
}

class SecretFixtureClass extends SecretFixture