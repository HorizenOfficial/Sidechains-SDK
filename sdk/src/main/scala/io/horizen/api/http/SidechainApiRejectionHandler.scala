package io.horizen.api.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._

object SidechainApiRejectionHandler {

  implicit val rejectionHandler = RejectionHandler.newBuilder()
    .handle {

      case ValidationRejection(msg, _) =>
        SidechainApiError(StatusCodes.BadRequest, "Validation failed").complete(msg)

      case MissingFormFieldRejection(fieldName) =>
        SidechainApiError(StatusCodes.BadRequest, "Missing Form Field").complete(s"The required ($fieldName) was not found.")

      case MissingQueryParamRejection(param) =>
        SidechainApiError(StatusCodes.BadRequest, "Missing Parameter").complete(s"The required ($param) was not found.")

      case MalformedQueryParamRejection(param, _, _) =>
        SidechainApiError(StatusCodes.BadRequest, "Malformed Parameter").complete(s"The required ($param) was not found.")

      case MalformedRequestContentRejection(msg, _) =>
        SidechainApiError(StatusCodes.BadRequest, "Malformed Request").complete(msg)

      case MethodRejection(_) =>
        SidechainApiError(StatusCodes.MethodNotAllowed, "HTTP method not allowed.")

      case RequestEntityExpectedRejection =>
        SidechainApiError(StatusCodes.BadRequest, "Request entity expected but not supplied")

    }
    .handleNotFound {
      SidechainApiError(StatusCodes.NotFound, "NotFound").complete("The requested resource could not be found.")
    }
    .result()
}
