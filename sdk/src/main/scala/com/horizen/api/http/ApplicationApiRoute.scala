package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.pattern.ask
import com.horizen.SidechainNodeViewHolder
import com.horizen.node.SidechainNodeView
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}

case class ApplicationApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, applicationApiGroup: ApplicationApiGroup)
                              (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute
  with ScorexEncoding
  with FunctionsApplierOnSidechainNodeView {

  override def route: Route = convertRoutes

  private def convertRoutes: Route = {
    applicationApiGroup.setFunctionsApplierOnSidechainNodeView(this)

    val listOfAppApis: List[Route] = applicationApiGroup.getRoutes.asScala.toList.map(r => r.asScala)

    pathPrefix(applicationApiGroup.basePath()) {
      listOfAppApis.reduceOption(_ ~ _).getOrElse(RouteDirectives.reject)
    }
  }

  override def applyFunctionOnSidechainNodeView[R](f: java.util.function.Function[SidechainNodeView, R]): R = {
    val messageToSend = SidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView(f)
    sendMessageToSidechainNodeView(messageToSend)
  }

  override def applyBiFunctionOnSidechainNodeView[T, R](f: java.util.function.BiFunction[SidechainNodeView, T, R], functionParameter: T): R = {
    val messageToSend = SidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView(f, functionParameter)
    sendMessageToSidechainNodeView(messageToSend)
  }

  private def sendMessageToSidechainNodeView[T, R](messageToSend: T): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? messageToSend).asInstanceOf[Future[R]]
      val result = Await.result[R](res, settings.timeout)
      result
    }
    catch  {
      case e: Exception => throw new Exception(e)
    }
  }
}
