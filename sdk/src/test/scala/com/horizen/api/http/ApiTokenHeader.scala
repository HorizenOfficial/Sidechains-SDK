package com.horizen.api.http

import akka.http.javadsl.model.headers.ModeledCustomHeader

final class ApiTokenHeader(name: String, value: String) extends ModeledCustomHeader(name: String, value: String) {

  override def renderInRequests = true
  override def renderInResponses = true
}
