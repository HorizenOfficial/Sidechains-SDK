package com.horizen.fixtures

import com.horizen.box.RegularBox

import java.util.{List => JList}
import java.util.{ArrayList => JArrayList}

trait BoxFixture extends SecretFixture{

  val rb1 : RegularBox = new RegularBox(pk1.publicImage, 1, 60)
  val rb2 : RegularBox = new RegularBox(pk2.publicImage, 1, 50)
  val rb3 : RegularBox = new RegularBox(pk3.publicImage, 1, 20)

  def getBoxList () : JList[RegularBox] = {
    val list : JList[RegularBox] = new JArrayList[RegularBox]()
    list.add(rb1)
    list.add(rb2)
    list.add(rb3)
    list
  }

}
