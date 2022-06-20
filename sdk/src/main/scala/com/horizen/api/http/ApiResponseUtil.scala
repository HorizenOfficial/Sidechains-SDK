package com.horizen.api.http

import akka.http.javadsl.model.{ContentTypes, HttpEntities, StatusCodes}
import akka.http.javadsl.server
import akka.http.javadsl.server.Directives
import akka.http.scaladsl.server.Route
import com.google.common.base.Throwables
import com.horizen.serialization.SerializationUtil

import scala.compat.java8.OptionConverters._

object ApiResponseUtil {

  def toResponse(response: ApiResponse): Route = {
    response match {
      case _: SuccessResponse => SidechainApiResponse(SerializationUtil.serializeWithResult(response))
      case e: ErrorResponse =>
        e.exception.asScala match {
          case Some(thr) =>
            val msg = thr.getMessage
            if (msg != null && !msg.isEmpty)
              SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, msg))
            else SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, ""))
          case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult(e.code, e.description, ""))
        }
    }
  }

  def toResponseWithoutResultWrapper(response: ApiResponse): Route = {
    response match {
      case _: SuccessResponse => SidechainApiResponse(SerializationUtil.serialize(response))
      case e: ErrorResponse =>
        e.exception.asScala match {
          case Some(thr) =>
            val msg = thr.getMessage
            if (msg != null && !msg.isEmpty)
              SidechainApiResponse(SerializationUtil.serialize(e.code, e.description, msg))
            else SidechainApiResponse(SerializationUtil.serialize(e.code, e.description, ""))
          case None => SidechainApiResponse(SerializationUtil.serialize(e.code, e.description, ""))
        }
    }
  }

  def toResponseAsJava(response: ApiResponse): server.Route = {
    response match {
      case _: SuccessResponse => Directives.complete(StatusCodes.OK, HttpEntities.create(ContentTypes.APPLICATION_JSON, SerializationUtil.serializeWithResult(response)))
      case e: ErrorResponse => {
        val exceptionMessage: String = e.exception.asScala.map(Throwables.getStackTraceAsString).getOrElse("")
        Directives.complete(StatusCodes.OK, HttpEntities.create(ContentTypes.APPLICATION_JSON, SerializationUtil.serializeErrorWithResult(e.code, e.description, exceptionMessage)))
      }
    }
  }
}
