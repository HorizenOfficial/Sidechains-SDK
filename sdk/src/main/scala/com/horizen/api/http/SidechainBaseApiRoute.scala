package com.horizen.api.http

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainNodeViewBase}
import com.horizen.AbstractSidechainNodeViewHolder.SidechainNodeViewBase
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.node.{NodeHistoryBase, NodeMemoryPool, NodeState, NodeWalletBase}
import scorex.core.api.http.{ApiDirectives, ApiRoute}

import scala.concurrent.{Await, ExecutionContext, Future}

trait SidechainBaseApiRoute[H <: NodeHistoryBase, S <: NodeState, W <: NodeWalletBase, P <: NodeMemoryPool, NV <: SidechainNodeViewBase[H,S,W,P]] extends ApiRoute with ApiDirectives{

  val sidechainNodeViewHolderRef: ActorRef

  implicit val ec: ExecutionContext



  def applyOnNodeView[R](functionToBeApplied: SidechainNodeViewBase[H,S,W,P] => R): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView(functionToBeApplied)).asInstanceOf[Future[R]]
      val result = Await.result[R](res, settings.timeout)
      result
    }
    catch {
      case e: Exception => throw new Exception(e)
    }

  }

  def withNodeView(f: SidechainNodeViewBase[H,S,W,P] => Route): Route = onSuccess(viewAsync())(f)

  protected def viewAsync(): Future[SidechainNodeViewBase[H,S,W,P]] = {
    def f(v: SidechainNodeViewBase[H,S,W,P]) = v

    (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
      .mapTo[SidechainNodeViewBase[H,S,W,P]]
  }



}