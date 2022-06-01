package com.horizen.api.http

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.node.{NodeHistoryBase, NodeMemoryPool, NodeMemoryPoolBase, NodeState, NodeWalletBase}
import com.horizen.transaction.Transaction
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainNodeViewBase}
import scorex.core.api.http.{ApiDirectives, ApiRoute}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}

trait SidechainBaseApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  NH <: NodeHistoryBase[TX, H, PM],
  S <: NodeState,
  W <: NodeWalletBase,
  P <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, NH, S, W, P]]
  extends ApiRoute with ApiDirectives {

  val sidechainNodeViewHolderRef: ActorRef

  implicit val ec: ExecutionContext
  implicit val tag: ClassTag[NV]


  //def applyOnNodeView[R](functionToBeApplied: SidechainNodeViewBase[H, S, W, P] => R): R = {
  def applyOnNodeView[R](functionToBeApplied: NV => R): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView(functionToBeApplied)).asInstanceOf[Future[R]]
      val result = Await.result[R](res, settings.timeout)
      result
    }
    catch {
      case e: Exception => throw new Exception(e)
    }

  }

  def withNodeView(f: NV => Route): Route = onSuccess(viewAsync())(f)

  protected def viewAsync(): Future[NV] = {
    def f(v: NV) = v


    (sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView(f))
      .mapTo[NV]
  }


}