package com.horizen.api.http.schema

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views

/**
  * A SidechainApiErrorResponseScheme represents a business logic error, not HTTP error.
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
case class SidechainApiErrorResponseScheme(error: SidechainApiManagedError)

@JsonView(Array(classOf[Views.Default]))
case class SidechainApiManagedError(code: String, description: String, detail: Option[String] = None)