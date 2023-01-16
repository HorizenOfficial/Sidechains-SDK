package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.pattern.ask
import com.horizen.AbstractSidechainNodeViewHolder
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.box.Box
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.node._
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction
import sparkz.core.api.http.{ApiDirectives, ApiRoute}
import sparkz.core.settings.RESTApiSettings
import sparkz.core.utils.SparkzEncoding
import com.horizen.node.SidechainNodeView
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}

case class ApplicationApiRoute(override val settings: RESTApiSettings, applicationApiGroup: ApplicationApiGroup, sidechainNodeViewHolderRef: ActorRef)
                              (implicit val context: ActorRefFactory)
  extends ApiRoute
    with ApiDirectives
    with SparkzEncoding
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
    val messageToSend = AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView[
      BoxTransaction[Proposition, Box[Proposition]],
      SidechainBlockHeader,
      SidechainBlock,
      SidechainFeePaymentsInfo,
      NodeHistory,
      NodeState,
      NodeWallet,
      NodeMemoryPool,
      SidechainNodeView,
      R](f)
    sendMessageToSidechainNodeView(messageToSend)
  }

  override def applyBiFunctionOnSidechainNodeView[T, R](f: java.util.function.BiFunction[SidechainNodeView, T, R], functionParameter: T): R = {
    val messageToSend = AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView[
      BoxTransaction[Proposition, Box[Proposition]],
      SidechainBlockHeader,
      SidechainBlock,
      SidechainFeePaymentsInfo,
      NodeHistory,
      NodeState,
      NodeWallet,
      NodeMemoryPool,
      SidechainNodeView,
      T,R](f, functionParameter)
    sendMessageToSidechainNodeView(messageToSend)
  }

  private def sendMessageToSidechainNodeView[T, R](messageToSend: T): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? messageToSend).asInstanceOf[Future[R]]
      val result = Await.result[R](res, settings.timeout)
      result
    }
    catch {
      case e: Exception => throw new Exception(e)
    }
  }
}
