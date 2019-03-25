package com.horizen.fixtures

import com.horizen.box.{CertifierRightBox, RegularBox}
import com.horizen.proposition.PublicKey25519Proposition
import java.util.{ArrayList => JArrayList, List => JList}

import scala.util.Random

trait BoxFixture extends SecretFixture{

  def getRegularBox () : RegularBox = {
    new RegularBox(getSecret().publicImage().asInstanceOf[PublicKey25519Proposition], 1, Random.nextInt(100))
  }

  def getRegularBoxList (count : Int) : JList[RegularBox] = {
    val boxList : JList[RegularBox] = new JArrayList[RegularBox]()

    for (i <- 1 to count)
      boxList.add(getRegularBox())

    boxList
  }

  def getCertifierRightBox () : CertifierRightBox = {
    new CertifierRightBox(getSecret().publicImage().asInstanceOf[PublicKey25519Proposition], 1, Random.nextInt(100))
  }

  def getCretifierRightBoxList (count : Int) : JList[CertifierRightBox] = {
    val boxList : JList[CertifierRightBox] = new JArrayList[CertifierRightBox]()

    for (i <- 1 to count)
      boxList.add(getCertifierRightBox())

    boxList
  }
}

class BoxFixtureClass extends BoxFixture