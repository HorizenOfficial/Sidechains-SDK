package com.horizen.api.http

import com.fasterxml.jackson.annotation.{JsonProperty, JsonView}
import com.horizen.serialization.Views

@JsonView(Array(classOf[Views.Default]))
case class POCDTOResponse(@JsonProperty("body") body : String) {

}
