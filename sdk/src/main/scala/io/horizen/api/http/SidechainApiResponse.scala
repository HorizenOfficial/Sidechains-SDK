package io.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import JacksonSupport._
import scala.language.implicitConversions

import scala.concurrent.Future

class SidechainApiResponse(statusCode: StatusCode) {

  def apply(result: String): Route = withString(result)

  def apply(result: Future[String]): Route = Directives.onSuccess(result)(withString)

  def defaultRoute: Route = withString(defaultMessage)

  def defaultMessage: String = statusCode.reason

  def withString(s: String): Route = complete(s)

  def complete(result: String): Route = {
    val httpEntity = HttpEntity(ContentTypes.`application/json`, result)
    Directives.complete(statusCode.intValue() -> httpEntity)
  }
}

object SidechainApiResponse {

  implicit def toRoute(route: SidechainApiResponse): Route = route.defaultRoute

  def apply(result: String): Route = OK(result)

  def apply(result: String, hasError: Boolean): Route =
    if (hasError) BAD_REQ(result) else OK(result)

  def apply(result: Future[String]): Route = OK(result)

  def apply(result: Either[Throwable, String]): Route = result.fold(SidechainApiError.apply, OK.apply)

  object OK extends SidechainApiResponse(StatusCodes.OK)

  object BAD_REQ extends SidechainApiResponse(StatusCodes.BadRequest)

}
