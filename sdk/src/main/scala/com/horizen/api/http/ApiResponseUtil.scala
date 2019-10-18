package com.horizen.api.http

import akka.http.scaladsl.server.Route
import com.horizen.serialization.SerializationUtil

object ApiResponseUtil {

  def toResponse(response: ApiResponse): Route = {
    response match {
      case _: SuccessResponse => SidechainApiResponse(SerializationUtil.serializeWithResult(response))
      case e: ErrorResponse =>
        e.exception match {
          case Some(thr) =>
            val msg = thr.getMessage
            if (msg != null && !msg.isEmpty)
              SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, msg))
            else SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, ""))
          case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, ""))
        }
    }
  }
}
