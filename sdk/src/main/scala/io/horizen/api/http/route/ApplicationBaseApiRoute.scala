package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.pattern.ask
import io.horizen.api.http.{ApplicationBaseApiGroup, FunctionsApplierOnSidechainNodeView}
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import io.horizen.transaction.Transaction
import io.horizen.{AbstractSidechainNodeViewHolder, SidechainNodeViewBase}
import sparkz.core.api.http.{ApiDirectives, ApiRoute}
import sparkz.core.settings.RESTApiSettings
import sparkz.util.SparkzEncoding

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}



abstract class ApplicationBaseApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](
                                  override val settings: RESTApiSettings,
                                  applicationApiGroup: ApplicationBaseApiGroup[TX, H, PM, FPI, NH, NS, NW, NP, NV],
                                  sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory)
  extends ApiRoute
    with ApiDirectives
    with SparkzEncoding
    with FunctionsApplierOnSidechainNodeView[
    TX,
    H,
    PM,FPI, NH, NS, NW, NP, NV
  ] {

  override def route: Route = convertRoutes

  private def convertRoutes: Route = {
    applicationApiGroup.setFunctionsApplierOnSidechainNodeView(this)

    val listOfAppApis: List[Route] = applicationApiGroup.getRoutes.asScala.toList.map(r => r.asScala)

    pathPrefix(applicationApiGroup.basePath()) {
      listOfAppApis.reduceOption(_ ~ _).getOrElse(RouteDirectives.reject)
    }
  }

  def sendMessageToSidechainNodeView[T, R](messageToSend: T): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? messageToSend).asInstanceOf[Future[R]]
      val result = Await.result[R](res, settings.timeout)
      result
    }
    catch {
      case e: Exception => throw new Exception(e)
    }
  }

  override def applyFunctionOnSidechainNodeView[R](f: java.util.function.Function[NV, R]): R = {
    val messageToSend = AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView[NV, R](f)
    sendMessageToSidechainNodeView(messageToSend)
  }

  override def applyBiFunctionOnSidechainNodeView[T, R](f: java.util.function.BiFunction[NV, T, R], functionParameter: T): R = {
    val messageToSend = AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView[NV, T, R](f, functionParameter)
    sendMessageToSidechainNodeView(messageToSend)
  }
}
