package io.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import scala.language.implicitConversions

case class SidechainApiError(statusCode: StatusCode, reason: String = "") {

  private val mapper = new ObjectMapper() with ScalaObjectMapper

  mapper.registerModule(DefaultScalaModule)
  mapper.enable(SerializationFeature.INDENT_OUTPUT)

  def apply(detail: String): Route = complete(detail)

  def defaultRoute: Route = complete()

  def complete(detail: String = ""): Route = {
    val nonEmptyReason = if (reason.isEmpty) statusCode.reason else reason
    val detailOpt = if (detail.isEmpty) None else Some(detail)

    case class HttpError(error: Int, reason: String, detail: Option[String] = None)

    val entity = HttpEntity(ContentTypes.`application/json`,
      mapper.writeValueAsString(
        HttpError(statusCode.intValue, nonEmptyReason, detailOpt)
      ))
    Directives.complete(statusCode.intValue() -> entity)
  }
}

object SidechainApiError {

  def apply(s: String): Route = InternalError(s)

  def apply(e: Throwable): Route = InternalError(safeMessage(e))

  def apply(causes: Seq[Throwable]): Route = InternalError(mkString(causes))

  def mkString(causes: Seq[Throwable]): String = causes.map(safeMessage).mkString(", ")

  private def safeMessage(e: Throwable): String = Option(e.getMessage).getOrElse(e.toString)

  implicit def toRoute(error: SidechainApiError): Route = error.defaultRoute

  object InternalError extends SidechainApiError(StatusCodes.InternalServerError, "internal.error")

  object InvalidJson extends SidechainApiError(StatusCodes.BadRequest, "invalid.json")

  object BadRequest extends SidechainApiError(StatusCodes.BadRequest, "bad.request")

  object ApiKeyNotValid extends SidechainApiError(StatusCodes.Forbidden, "invalid.api-key")

  object NotExists extends SidechainApiError(StatusCodes.NotFound, "not-found")

  object Forbidden extends SidechainApiError(StatusCodes.Forbidden, "forbidden")

}
