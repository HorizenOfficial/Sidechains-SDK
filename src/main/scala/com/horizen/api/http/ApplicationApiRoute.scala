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

      override def serialize(anObject: Any): String =
        ApplicationApiRoute.this.serialize(anObject)

      override def serialize(anObject: Any, aView: Class[_]): String =
        ApplicationApiRoute.this.serialize(anObject, aView)

      override def serialize(anObject: Any, sidechainJsonSerializer: SidechainJsonSerializer): String =
        ApplicationApiRoute.this.serialize(anObject, sidechainJsonSerializer = sidechainJsonSerializer)

      override def serialize(anObject: Any, aView: Class[_], sidechainJsonSerializer: SidechainJsonSerializer): String =
        ApplicationApiRoute.this.serialize(anObject, aView, sidechainJsonSerializer)

      override def serializeError(code: String, description: String, detail: String): String =
        ApplicationApiRoute.this.serializeError(code, description, Option(detail))

      override def serializeError(code: String, description: String, detail: String, aView: Class[_]): String =
        ApplicationApiRoute.this.serializeError(code, description, Option(detail), aView)

      override def serializeError(code: String, description: String, detail: String, sidechainJsonSerializer: SidechainJsonSerializer): String =
        ApplicationApiRoute.this.serializeError(code, description, Option(detail), sidechainJsonSerializer = sidechainJsonSerializer)

      override def serializeError(code: String, description: String, detail: String, aView: Class[_], sidechainJsonSerializer: SidechainJsonSerializer): String =
        ApplicationApiRoute.this.serializeError(code, description, Option(detail), aView, sidechainJsonSerializer)

      override def newJsonSerializer: SidechainJsonSerializer = newSidechainJsonSerializer()

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
