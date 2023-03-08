package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.RouteDirectives
import io.horizen.{AbstractSidechainNodeViewHolder, SidechainTypes}
import akka.pattern.ask
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.api.http.FunctionsApplierOnSidechainNodeView
import io.horizen.node.NodeWalletBase
import io.horizen.utxo.api.http.SidechainApplicationApiGroup
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import sparkz.core.api.http.{ApiDirectives, ApiRoute}
import sparkz.core.settings.RESTApiSettings
import sparkz.util.SparkzEncoding

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}

case class AccountApplicationApiRoute(override val settings: RESTApiSettings, applicationApiGroup: SidechainApplicationApiGroup, sidechainNodeViewHolderRef: ActorRef)
                                       (implicit val context: ActorRefFactory)
  extends ApiRoute
    with ApiDirectives
    with SparkzEncoding
    with FunctionsApplierOnSidechainNodeView[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView
  ] {


  override def route: Route = convertRoutes

  private def convertRoutes: Route = {
    applicationApiGroup.setFunctionsApplierOnSidechainNodeView(this)

    val listOfAppApis: List[Route] = applicationApiGroup.getRoutes.asScala.toList.map(r => r.asScala)

    pathPrefix(applicationApiGroup.basePath()) {
      listOfAppApis.reduceOption(_ ~ _).getOrElse(RouteDirectives.reject)
    }
  }

  override def applyFunctionOnSidechainNodeView[R](f: java.util.function.Function[AccountNodeView, R]): R = {
    val messageToSend = AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyFunctionOnNodeView[
      AccountNodeView,
      R](f)
    sendMessageToSidechainNodeView(messageToSend)
  }

  override def applyBiFunctionOnSidechainNodeView[T, R](f: java.util.function.BiFunction[AccountNodeView, T, R], functionParameter: T): R = {
    val messageToSend = AbstractSidechainNodeViewHolder.ReceivableMessages.ApplyBiFunctionOnNodeView[
      AccountNodeView,
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
