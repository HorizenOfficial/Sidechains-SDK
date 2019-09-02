package com.horizen.api.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server._
import scorex.core.api.http.ApiError

object SidechainApiRejectionHandler {

  implicit val rejectionHandler = RejectionHandler.newBuilder()
    .handle{

      case ValidationRejection(msg, cause) =>
        ApiError(StatusCodes.BadRequest, "Validation failed").complete(msg)

      case MissingFormFieldRejection(fieldName) =>
        ApiError(StatusCodes.BadRequest, "Missing Form Field").complete(s"The required ($fieldName) was not found.")

      case MissingQueryParamRejection(param) =>
        ApiError(StatusCodes.BadRequest, "Missing Parameter").complete(s"The required ($param) was not found.")

      case MalformedQueryParamRejection(param, msg, exp) =>
        ApiError(StatusCodes.BadRequest, "Malformed Parameter").complete(s"The required ($param) was not found.")

      case MalformedRequestContentRejection(msg, exp) =>
        ApiError(StatusCodes.BadRequest, "Malformed Request").complete(msg)

      case MethodRejection(method) =>
        ApiError(StatusCodes.MethodNotAllowed, "HTTP method not allowed.")

      case RequestEntityExpectedRejection =>
        ApiError(StatusCodes.BadRequest, "Request entity expected but not supplied")

    }
      .handleNotFound { ApiError(StatusCodes.NotFound, "NotFound").complete("The requested resource could not be found.") }
      .result()
}
