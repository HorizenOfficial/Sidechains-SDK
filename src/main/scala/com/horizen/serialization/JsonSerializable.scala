package com.horizen.serialization

import io.circe.Json

trait JsonSerializable {

  type M >: this.type <: JsonSerializable

  def json: Json = jsonSerializer.toJson(this)

  def jsonSerializer: JsonSerializer[M]

}
