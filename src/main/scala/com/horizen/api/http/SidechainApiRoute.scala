package com.horizen.api.http

import akka.actor.ActorRef
import com.horizen.node.SidechainNodeView
import scorex.core.api.http.{ApiDirectives, ApiRoute}
import akka.pattern.ask
import akka.http.scaladsl.server.Route
import com.horizen.api.ActorRegistry
import scorex.core.utils.ScorexEncoding

import scala.concurrent.Future

trait SidechainApiRoute extends ApiRoute with ApiDirectives
  with ScorexEncoding {

  import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView

  val sidechainNodeViewHolderRef: ActorRef

  val sidechainExtendedActorRegistry : ActorRegistry

  def withNodeView(f: SidechainNodeView => Route): Route = onSuccess(viewAsync())(f)

  protected def viewAsync(): Future[SidechainNodeView] = {
    def f(v: SidechainNodeView) = v
    (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
      .mapTo[SidechainNodeView]
  }

}

