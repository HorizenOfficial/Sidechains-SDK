package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import scorex.core.api.http.ApiResponse
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

case class SidechainUtilsApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec : ExecutionContext) extends SidechainApiRoute {

  override val route : Route = (pathPrefix("util"))
            {dbLog ~ setMockTime}

  def dbLog : Route = (post & path("dbLog"))
  {
    ApiResponse.OK
  }

  def setMockTime : Route = (post & path("setMockTime"))
  {
    ApiResponse.OK
  }

}
