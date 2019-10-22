package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

/**
  * A developer can disable some core api.
  *
  * 1) Akka library doesn't provide a way, given a Route, to access to its Directives and modify them.
  * 2) Akka library evaluates the route to be executed by selecting those match the uri path and method. The first one matched is executed.
  * 3) SidechainApiRoute, the trait implemented by all core apis, has a variable of type Route initialized at compile time.
  *
  * Given 1), 2) and 3), and in order to avoid to apply changes to all core apis, it's possible to disable some uri path
  * by adding a route with a reject directive for that uri path.
  *
  * When the application starts, all routes are concatenated in a single composed route.
  * These rejection routes should be concatenated as first.
  */
case class SidechainRejectionApiRoute(basePath: String, path: String,
                                      override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                     (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute {

  override def route: Route =
    if (path.isEmpty)
      (pathPrefix(basePath)) {
        SidechainApiError(StatusCodes.NotFound, "NotFound").complete("The requested resource could not be found.")
      }
    else (pathPrefix(basePath) & path(path)) {
      SidechainApiError(StatusCodes.NotFound, "NotFound").complete("The requested resource could not be found.")
    }

}
