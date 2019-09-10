package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import com.horizen.node.SidechainNodeView
import scorex.core.api.http.ApiDirectives
import akka.pattern.ask
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers
import akka.util.Timeout
import scorex.core.utils.ActorHelper
import scorex.util.ScorexLogging

import scala.concurrent.{ExecutionContext, Future}

trait SidechainApiRoute  extends ApiDirectives with ActorHelper with PredefinedFromEntityUnmarshallers with ScorexLogging {

  import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView

  implicit lazy val timeout: Timeout = Timeout(settings.timeout)
  def context: ActorRefFactory
  def route: Route
  override val apiKeyHeaderName: String = "api_key"

  val sidechainNodeViewHolderRef: ActorRef

  implicit val ec : ExecutionContext

  def withNodeView(f: SidechainNodeView => Route): Route = onSuccess(viewAsync())(f)

  protected def viewAsync(): Future[SidechainNodeView] = {
    def f(v: SidechainNodeView) = v
    (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
      .mapTo[SidechainNodeView]
  }

}

