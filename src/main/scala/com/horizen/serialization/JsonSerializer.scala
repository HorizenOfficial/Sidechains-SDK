package com.horizen.serialization

import io.circe.Json

import scala.util.Try

trait JsonSerializer[T <: JsonSerializable] {

  def toJson(obj : T) : Json = obj.toJson

  def parseJson(json: Json) : T = ???

  def parseJsonTry(json: Json) : Try[T] = Try {
    parseJson(json)
  }
}
