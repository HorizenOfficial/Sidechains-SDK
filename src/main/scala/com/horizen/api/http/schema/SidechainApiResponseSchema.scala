package com.horizen.api.http.schema

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views

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
  * For business logic errors use SidechainApiErrorResponseScheme
  */
@JsonView(Array(classOf[Views.Default]))
case class SidechainApiResponseBody(result: Any)
