package com.horizen.account.api.http

object Test extends App {
  test()
  def test() = {

    println(otherMethod())

  }


  def otherMethod(): String = {
    val maybeStr: Option[String] = None
    maybeStr match {
      case Some(str) => return str + "first"
      case None =>
    }

    return "second"
  }
}
