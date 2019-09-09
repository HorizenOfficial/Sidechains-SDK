package com.horizen.api.http

import akka.actor.ActorRef
import com.horizen.node.SidechainNodeView
import scorex.core.api.http.{ApiDirectives, ApiRoute}
import akka.pattern.ask
import akka.http.scaladsl.server.Route
import com.horizen.api.http.schema.{SidechainApiErrorResponseScheme, SidechainApiManagedError, SidechainApiResponseBody}
import scorex.core.utils.ScorexEncoding

import scala.concurrent.{ExecutionContext, Future}

trait SidechainApiRoute extends ApiRoute with ApiDirectives
  with ScorexEncoding {

  import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView

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

