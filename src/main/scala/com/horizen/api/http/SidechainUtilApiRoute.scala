package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

case class SidechainUtilApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                (implicit val context: ActorRefFactory, override val ec : ExecutionContext) extends SidechainApiRoute {

  override val route : Route = (pathPrefix("util"))
            {dbLog ~ setMockTime}

  def dbLog : Route = (post & path("dbLog"))
  {
    SidechainApiResponse.OK
  }

  def setMockTime : Route = (post & path("setMockTime"))
  {
    SidechainApiResponse.OK
  }

}
