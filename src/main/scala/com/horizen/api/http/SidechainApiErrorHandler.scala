package com.horizen.api.http

import akka.http.scaladsl.server.ExceptionHandler
import scorex.core.api.http.ApiError

import scala.util.control.NonFatal

class SidechainApiErrorHandler {

  implicit val exceptionHandler : ExceptionHandler = ExceptionHandler{
    case NonFatal(exp) => ApiError(exp.getCause)
  }
}
