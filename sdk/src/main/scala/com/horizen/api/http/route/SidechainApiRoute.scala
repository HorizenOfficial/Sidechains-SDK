package com.horizen.api.http.route

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import com.horizen.transaction.Transaction
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainNodeViewBase}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.api.http.{ApiDirectives, ApiRoute}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

trait SidechainApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  S <: NodeStateBase,
  W <: NodeWalletBase,
  P <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, S, W, P]] extends ApiRoute with ApiDirectives {


  val sidechainNodeViewHolderRef: ActorRef

  implicit val ec: ExecutionContext
  implicit val tag: ClassTag[NV]

  /**
   * Get an access to the SidechainNodeView and execute the method locking the NodeViewHolder
   * to guarantee that the data retrieved is synchronized (no write operations occurred between two read operations).
   *
   * @param functionToBeApplied - generic function to be applied on the SidechainNodeView in NodeViewHolder thread.
   * @tparam R - generic result type of {@param functionToBeApplied}
   * @return instance of {@tparam R}
   */
  def applyOnNodeView[R](functionToBeApplied: NV => R): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView(functionToBeApplied)).asInstanceOf[Future[R]]
      val result = Await.result[R](res, settings.timeout)
      result
    }
    catch {
      case e: Exception => throw new Exception(e)
    }

  }

  /**
   * Utility method to execute {@param f} method on the SidechainNodeView without locking it for modifications.
   * Note: in case of multiple read operations, may return inconsistent data,
   * because of backed DBs modification in a parallel thread.
   *
   * @param f - function to be applied on the SidechainNodeView
   * @return The instance of {@code Route}.
   */
  def withNodeView(f: NV => Route): Route = onSuccess(viewAsync())(f)

  protected def viewAsync(): Future[NV] = {
    def f(v: NV) = v


    (sidechainNodeViewHolderRef ? AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView(f))
      .mapTo[NV]
  }


  type View = CurrentView[NH, S, W, P]

  def withView(f: View => Route): Route = onSuccess(sidechainViewAsync())(f)

  protected def sidechainViewAsync(): Future[View] = {
    def f(v: View) = v

    (sidechainNodeViewHolderRef ? GetDataFromCurrentView(f)).mapTo[View]
  }

}
