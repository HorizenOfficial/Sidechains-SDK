package com.horizen.api.http.dto

import com.fasterxml.jackson.annotation.JsonProperty

case class POCDTORequest(
                          @JsonProperty("requestBody")
                        body : String) {

}
