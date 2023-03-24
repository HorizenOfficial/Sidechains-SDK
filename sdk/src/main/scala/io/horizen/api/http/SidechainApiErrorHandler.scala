package io.horizen.api.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler

import scala.util.control.NonFatal

object SidechainApiErrorHandler {

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case NonFatal(exp) =>
      SidechainApiError(StatusCodes.InternalServerError, "Unexpected error").complete(exp.getMessage)
  }
}
