package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

case class SidechainRejectionApiRoute(basePath : String, path : String,
                                      override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                     (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
  extends SidechainApiRoute {

  override def route: Route = {
    if (path.isEmpty) {
      (pathPrefix(basePath)) {
        {reject}
      }
    } else {
      (pathPrefix(basePath) & path(path)) {
        {reject}
      }
    }

  }

}
