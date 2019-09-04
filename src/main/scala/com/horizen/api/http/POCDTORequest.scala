package com.horizen.api.http

import com.fasterxml.jackson.annotation.JsonProperty

case class POCDTORequest(
                          @JsonProperty("requestBody")
                        body : String) {

}
