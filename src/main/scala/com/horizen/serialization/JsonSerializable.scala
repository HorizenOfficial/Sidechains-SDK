package com.horizen.serialization

import io.circe.Json

trait JsonSerializable {

  type J >: this.type  <: JsonSerializable

  def toJson : Json

  def jsonSerializer : JsonSerializer[J]
}
