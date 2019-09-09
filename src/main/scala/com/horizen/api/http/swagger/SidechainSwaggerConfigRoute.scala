package com.horizen.api.http.swagger

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Route
import com.horizen.api.http.SidechainApiRoute
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

class SidechainSwaggerConfigRoute(swaggerConf: String,
                                 override val settings: RESTApiSettings, override val sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
  extends SidechainApiRoute {

  override val route: Route = {
    (get & path("api-docs" / "swagger.conf")) {
      complete(HttpEntity(ContentTypes.`application/json`, swaggerConf))
    }
  }
}