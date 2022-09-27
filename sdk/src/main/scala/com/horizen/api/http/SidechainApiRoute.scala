package com.horizen.api.http

import akka.actor.ActorRef
import com.horizen.node.SidechainNodeView
import sparkz.core.api.http.{ApiDirectives, ApiRoute}
import akka.pattern.ask
import akka.http.scaladsl.server.Route
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import scala.concurrent.{Await, ExecutionContext, Future}

trait SidechainApiRoute extends ApiRoute with ApiDirectives {

  import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView

  val sidechainNodeViewHolderRef: ActorRef

  implicit val ec: ExecutionContext

  /**
   * Get an access to the SidechainNodeView and execute the method locking the NodeViewHolder
   * to guarantee that the data retrieved is synchronized (no write operations occurred between two read operations).
   *
   * @param functionToBeApplied - generic function to be applied on the SidechainNodeView in NodeViewHolder thread.
   * @tparam R - generic result type of {@param functionToBeApplied}
   * @return instance of {@tparam R}
   */
  def applyOnNodeView[R](functionToBeApplied: SidechainNodeView => R): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(functionToBeApplied)).asInstanceOf[Future[R]]
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
   * @param f - function to be applied on the SidechainNodeView
   * @return The instance of {@code Route}.
   */
  def withNodeView(f: SidechainNodeView => Route): Route = onSuccess(viewAsync())(f)

  protected def viewAsync(): Future[SidechainNodeView] = {
    def f(v: SidechainNodeView) = v

    (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
      .mapTo[SidechainNodeView]
  }

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  def withView(f: View => Route): Route = onSuccess(sidechainViewAsync())(f)

  protected def sidechainViewAsync(): Future[View] = {
    def f(v: View) = v
    (sidechainNodeViewHolderRef ? GetDataFromCurrentView(f)).mapTo[View]
  }

}

