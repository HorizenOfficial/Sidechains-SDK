package com.horizen.serialization

import io.circe.Json

import scala.util.Try

trait JsonSerializable {

  type J >: this.type  <: JsonSerializable

  def toJson : Json

  //def parseJson(json: Json) : J = ???
}