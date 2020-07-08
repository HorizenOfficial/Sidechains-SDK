package com.horizen.api.http

import java.util.Optional

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views

trait ApiResponse {

}

trait SuccessResponse extends ApiResponse {

}

trait ErrorResponse extends ApiResponse {
  val code: String
  val description: String
  val exception: Option[Throwable]
}

trait InternalErrorResponse extends ApiResponse{
  val exception: Optional[Throwable]
}

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