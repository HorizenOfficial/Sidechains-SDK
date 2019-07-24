package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.ActorRegistry
import scorex.core.api.http.ApiResponse
import scorex.core.settings.RESTApiSettings

case class SidechainUtilApi (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                             sidechainExtendedActorRegistry : ActorRegistry) (implicit val context: ActorRefFactory) extends SidechainApiRoute {

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
