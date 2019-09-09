package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import com.horizen.node.SidechainNodeView
import scorex.core.api.http.ApiDirectives
import akka.pattern.ask
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers
import com.horizen.api.http.schema.{SidechainApiErrorResponseScheme, SidechainApiManagedError, SidechainApiResponseBody}
import scorex.core.utils.ActorHelper
import scorex.util.ScorexLogging

import scala.language.implicitConversions
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

trait SidechainApiRoute
  extends ApiDirectives
    with ActorHelper
    with PredefinedFromEntityUnmarshallers
    with ScorexLogging {

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

  private val sidechainJsonSerializer = {
    var sjs = new SidechainJsonSerializer()
    sjs.setDefaultConfiguration()
    sjs
  }

  protected final def serialize(value : Any, view : Class[_] = sidechainJsonSerializer.getDefaultView, sidechainJsonSerializer : SidechainJsonSerializer = sidechainJsonSerializer) : String = {
    sidechainJsonSerializer.serialize(
      SidechainApiResponseBody(value), view)
  }

  protected final def serializeError(code : String, description : String, detail : Option[String] = None, view : Class[_] = sidechainJsonSerializer.getDefaultView, sidechainJsonSerializer : SidechainJsonSerializer = sidechainJsonSerializer) : String = {
    sidechainJsonSerializer.serialize(
      SidechainApiErrorResponseScheme(
        SidechainApiManagedError(code, description, detail)), view)
  }

  protected def newSidechainJsonSerializer() : SidechainJsonSerializer = {
    var sjs = new SidechainJsonSerializer()
    sjs.setDefaultConfiguration()
    sjs
  }

}

