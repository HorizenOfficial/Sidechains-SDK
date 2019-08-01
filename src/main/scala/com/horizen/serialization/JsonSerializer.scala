package com.horizen.serialization

import io.circe.Json

import scala.util.Try

trait JsonSerializer[T <: JsonSerializable] {

  def toJson(obj : T) : Json

  def tryParseJson: Try[T]

}
