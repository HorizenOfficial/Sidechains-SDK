package com.horizen.api.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe.Json
import scorex.core.api.http.ApiResponse

object SidechainApiResponse extends ApiResponse(StatusCodes.OK) {

  override def complete(result: Json): Route = super.complete(Json.obj("result" -> result))

  def apply(error : SidechainApiErrorResponse): Route = super.complete(
    Json.obj("error"-> Json.obj(
      ("code", Json.fromString(error.code)),
      ("description", Json.fromString(error.description)),
      ("detail", Json.fromString(error.detail))))
  )
}
