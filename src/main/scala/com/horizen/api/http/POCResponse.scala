package scorex.core.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}

import scala.concurrent.Future
import scala.language.implicitConversions

class POCResponse(statusCode: StatusCode) {

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

object POCResponse {
  implicit def toRoute(jsonRoute: ApiResponse): Route = jsonRoute.defaultRoute

  def apply(result: String): Route = OK(result)
  def apply(result: Future[String]): Route = OK(result)

  object OK extends ApiResponse(StatusCodes.OK)
}