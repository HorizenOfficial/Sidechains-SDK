package com.horizen.api.http.dto

import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty, JsonView}
import com.horizen.serialization.Views

@JsonView(Array(classOf[Views.Default]))
case class POCDTOResponse(@JsonProperty("body") body : String) {

}
