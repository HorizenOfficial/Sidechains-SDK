package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives
import com.horizen.api.http.swagger.SidechainSwaggerConfigRoute
import scorex.core.api.http.CorsHandler
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

case class SidechainCompositeHttpService(system: ActorSystem, routes: Seq[SidechainApiRoute], settings: RESTApiSettings, swaggerConf: String,
                                         implicit val sidechainNodeViewHolderRef: ActorRef)
                                        (implicit val context: ActorRefFactory, implicit val ec : ExecutionContext)
  extends CorsHandler {

  implicit val actorSystem: ActorSystem = system

  val swaggerService: SidechainSwaggerConfigRoute = new SidechainSwaggerConfigRoute(swaggerConf: String, settings, sidechainNodeViewHolderRef)

  val redirectToSwagger: Route = path("" | "/") {
    redirect("/swagger", StatusCodes.PermanentRedirect)
  }

  val compositeRoute: Route =
    routes.map(_.route).reduceOption(_ ~ _).getOrElse(RouteDirectives.reject) ~
      swaggerService.route ~
      path("swagger")(getFromResource("swagger-ui/index.html")) ~
      getFromResourceDirectory("swagger-ui") ~
      redirectToSwagger

}