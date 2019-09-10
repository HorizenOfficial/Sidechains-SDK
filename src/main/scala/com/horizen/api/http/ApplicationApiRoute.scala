package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.pattern.ask
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.node.SidechainNodeView
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

import scala.concurrent.{Await, ExecutionContext}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class ApplicationApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, applicationApiGroup : ApplicationApiGroup)
                              (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
                    extends SidechainApiRoute
                    with ScorexEncoding {


  override def route: Route = convertRoutes

  private def convertRoutes : Route = {

    applicationApiGroup.setApplicationNodeViewProvider(new ApplicationNodeViewProvider {

      override def getSidechainNodeView: Try[SidechainNodeView] = retrieveSidechainNodeView()

    })

    var listOfAppApis : List[Route] = applicationApiGroup.getRoutes.asScala.toList.map(r => r.asScala)

    pathPrefix(applicationApiGroup.basePath())
          { listOfAppApis.reduceOption(_ ~ _).getOrElse(RouteDirectives.reject) }
  }

  private def retrieveSidechainNodeView() : Try[SidechainNodeView] = {
    def f(v: SidechainNodeView) = v
    def fut = ((sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
      .mapTo[SidechainNodeView])

    try {
      var result = Await.result(fut, settings.timeout)
      Success(result)
    }catch {
      case e : Throwable => Failure(e)
    }
  }

}
