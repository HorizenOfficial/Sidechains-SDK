package com.horizen.serialization

import io.circe.Json
import scorex.core.serialization.BytesSerializable

trait JsonSerializable extends BytesSerializable{

  override type M >: this.type <: JsonSerializable

  def json: Json = jsonSerializer.toJson(this)

  def jsonSerializer: JsonSerializer[M]

}
