package io.horizen.api.http

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.json.Views

import java.util.{Optional => JOptional}

private[horizen] trait ApiResponse {

}

trait SuccessResponse extends ApiResponse {

}

trait ErrorResponse extends ApiResponse {
  val code: String
  val description: String
  val exception: JOptional[Throwable]
}

abstract class InternalErrorResponse extends ErrorResponse{
  override val description: String = "Unexpected exception during request processing"
  override val code: String = "500"
}

class InternalExceptionApiErrorResponse(override val exception: JOptional[Throwable]) extends InternalErrorResponse

/**
  * General structure of core Api responses.
  * Each response will be serialized with the following format:
  *
  *   {
  *     "result": {
  *       'the effective result
  *     }
  *   }
  *
  * For business logic errors use SidechainApiErrorResponseSchema
  */
@JsonView(Array(classOf[Views.Default]))
case class SidechainApiResponseBody(result: Any)

/**
  * A SidechainApiErrorResponseSchema represents a business logic error, not HTTP error.
  * Each business logic error will be serialized with the following format:
  *
  *    {
  *       "error": {
  *           "code": ...
  *           "description": ...
  *           "detail": ...
  *       }
  *    }
  *
  */
@JsonView(Array(classOf[Views.Default]))
case class SidechainApiErrorResponseSchema(error: SidechainApiManagedError)

@JsonView(Array(classOf[Views.Default]))
case class SidechainApiManagedError(code: String, description: String, detail: Option[String] = None)