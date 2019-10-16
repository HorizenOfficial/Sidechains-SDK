package com.horizen.fixtures

import scorex.core.{NodeViewModifier, bytesToId, idToBytes}

import com.horizen.box.{Box, BoxSerializer, CertifierRightBox, RegularBox}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.Secret
import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.{SidechainTypes, WalletBox}

import scala.util.Random
import com.horizen.customtypes._
import com.horizen.utils.BytesUtils

import scala.collection.JavaConverters._


trait BoxFixture
  extends SecretFixture
  with SidechainTypes
{

  def getRegularBox () : RegularBox = {
    new RegularBox(getSecret().publicImage().asInstanceOf[PublicKey25519Proposition], 1, Random.nextInt(100))
  }

  def getRegularBox (secret : Secret, nonce: Int, value: Int) : RegularBox = {
    new RegularBox(secret.publicImage().asInstanceOf[PublicKey25519Proposition], nonce, value)
  }

  def getRegularBoxList (count : Int) : JList[RegularBox] = {
    val boxList : JList[RegularBox] = new JArrayList[RegularBox]()

    for (i <- 1 to count)
      boxList.add(getRegularBox())

    boxList
  }

  def getRegularBoxList (secretList : JList[Secret]) : JList[RegularBox] = {
    val boxList : JList[RegularBox] = new JArrayList[RegularBox]()

    for (s <- secretList.asScala)
      boxList.add(getRegularBox(s, 1, Random.nextInt(100)))

    boxList
  }

  def getCertifierRightBox () : CertifierRightBox = {
    new CertifierRightBox(getSecret().publicImage().asInstanceOf[PublicKey25519Proposition], 1, Random.nextInt(100), Random.nextInt(100))
  }

  def getCretifierRightBoxList (count : Int) : JList[CertifierRightBox] = {
    val boxList : JList[CertifierRightBox] = new JArrayList[CertifierRightBox]()

    for (i <- 1 to count)
      boxList.add(getCertifierRightBox())

    boxList
  }

  def getCustomBox () : CustomBox = {
    new CustomBox(getCustomSecret().publicImage().asInstanceOf[CustomPublicKeyProposition], Random.nextInt(100))
  }

  def getCustomBoxList (count : Int) : JList[CustomBox] = {
    val boxList : JList[CustomBox] = new JArrayList[CustomBox]()

    for (i <- 1 to count)
      boxList.add(getCustomBox())

    boxList
  }


  def getWalletBox (boxClass : Class[_ <: Box[_ <: Proposition]]) : WalletBox = {
    val txId = new Array[Byte](32)
    Random.nextBytes(txId)

    boxClass match {
      case v if v == classOf[RegularBox] => new WalletBox(getRegularBox(), bytesToId(txId), Random.nextInt(100000))
      case v if v == classOf[CertifierRightBox] => new WalletBox(getCertifierRightBox(), bytesToId(txId), Random.nextInt(100000))
      case v if v == classOf[CustomBox] => new WalletBox(getCustomBox().asInstanceOf[SidechainTypes#SCB], bytesToId(txId), Random.nextInt(100000))
      case _ => null
    }
  }

  def getWalletBoxList (boxClass : Class[_ <: Box[_ <: Proposition]], count : Int) : JList[WalletBox] = {
    val boxList : JList[WalletBox] = new JArrayList[WalletBox]()

    for (i <- 1 to count)
      boxList.add(getWalletBox(boxClass))

    boxList
  }

  def getWalletBoxList (boxList : JList[SidechainTypes#SCB]) : JList[WalletBox] = {
    val wboxList : JList[WalletBox] = new JArrayList[WalletBox]()
    val txId = new Array[Byte](32)
    Random.nextBytes(txId)

    for (b <- boxList.asScala)
      wboxList.add(new WalletBox(b, bytesToId(txId), Random.nextInt(100000)))

    wboxList
  }

}

class BoxFixtureClass extends BoxFixture