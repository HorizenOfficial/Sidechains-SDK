package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.http.schema.{SidechainApiErrorCodeList, SidechainApiErrorCodeSchema, SidechainApiErrorCodeUtil}
import com.horizen.serialization.SerializationUtil
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

case class SidechainUtilsApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec : ExecutionContext) extends SidechainApiRoute {

  override val route: Route = (pathPrefix("util")) {
    errorCodes
  }

  def dbLog: Route = (post & path("dbLog")) {
    SidechainApiResponse.OK
  }

  def setMockTime: Route = (post & path("setMockTime")) {
    SidechainApiResponse.OK
  }

  def errorCodes: Route = (post & path("errorCodes")) {
    try {
      var codes = SidechainApiErrorCodeUtil.predefinedApiErrorCodes
      var res: Seq[SidechainApiErrorCodeSchema] = codes.map(err =>
        SidechainApiErrorCodeSchema(err.groupName, err.groupCode, err.internalName, err.internalCode, err.apiCode))

      SidechainApiResponse(
        SerializationUtil.serializeWithResult(SidechainApiErrorCodeList(res.toList))
      )

    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

}
