package io.horizen.utxo.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.pattern.ask
import io.horizen.api.http.route.ApplicationBaseApiRoute
import io.horizen.utxo.api.http.SidechainApplicationApiGroup
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.node._
import io.horizen.{AbstractSidechainNodeViewHolder, SidechainTypes}
import sparkz.core.settings.RESTApiSettings
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}

case class SidechainApplicationApiRoute(override val settings: RESTApiSettings, applicationApiGroup: SidechainApplicationApiGroup, sidechainNodeViewHolderRef: ActorRef)
                                       (implicit override val context: ActorRefFactory)
  extends ApplicationBaseApiRoute[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] (settings, applicationApiGroup, sidechainNodeViewHolderRef){


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
      SidechainNodeView,
      R](f)
    sendMessageToSidechainNodeView(messageToSend)
  }

  override def applyBiFunctionOnSidechainNodeView[T, R](f: java.util.function.BiFunction[SidechainNodeView, T, R], functionParameter: T): R = {
    val messageToSend = AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView[
      SidechainNodeView,
      T, R](f, functionParameter)
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
