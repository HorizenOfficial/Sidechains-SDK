package com.horizen.api.http

import akka.http.scaladsl.server.Route
import com.horizen.serialization.SerializationUtil

object ApiResponseUtil {

  def toResponse(response: ApiResponse): Route = {
    response match {
      case _: SuccessResponse => SidechainApiResponse(SerializationUtil.serializeWithResult(response))
      case e: ErrorResponse =>
        e.exception match {
          case Some(thr) => SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, thr.getMessage))
          case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, ""))
        }
    }
  }
}
