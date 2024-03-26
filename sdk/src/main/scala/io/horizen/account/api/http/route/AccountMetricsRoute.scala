package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.databind.JsonNode
import io.horizen.SidechainTypes
import io.horizen.account.api.http.route.AccountEthRpcRejectionHandler.rejectionHandler
import io.horizen.account.api.rpc.service.RpcProcessor
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.SidechainApiResponse
import io.horizen.api.http.route.SidechainApiRoute
import io.horizen.node.NodeWalletBase
import io.horizen.utils.ClosableResourceHandler
import io.prometheus.metrics.exporter.common.PrometheusScrapeHandler
import io.prometheus.metrics.expositionformats.ExpositionFormats
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.MetricSnapshots
import sparkz.core.api.http.ApiDirectives
import sparkz.core.settings.RESTApiSettings
import sparkz.util.SparkzLogging

import java.io.{ByteArrayOutputStream, StringWriter}
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class AccountMetricsRoute(
    override val settings: RESTApiSettings,
    sidechainNodeViewHolderRef: ActorRef,
    rpcProcessor: RpcProcessor
)(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
    extends SidechainApiRoute[
      SidechainTypes#SCAT,
      AccountBlockHeader,
      AccountBlock,
      AccountFeePaymentsInfo,
      NodeAccountHistory,
      NodeAccountState,
      NodeWalletBase,
      NodeAccountMemoryPool,
      AccountNodeView
    ]
      with SidechainTypes
      with ClosableResourceHandler
      with SparkzLogging
      with ApiDirectives {

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])
  override val route: Route = pathPrefix("metrics") {
    metrics
  }

  /**
   * Returns the success / error response of called rpc method or error if method does not exist
   */
  def metrics: Route = handleRejections(rejectionHandler) {
    get {
          entity(as[JsonNode]) { body =>
            {
              val snapshots = PrometheusRegistry.defaultRegistry.scrape
              val stream = new ByteArrayOutputStream
              ExpositionFormats.init.getPrometheusTextFormatWriter.write(stream, snapshots)
              SidechainApiResponse(stream.toString, false)
            }
      }
    }
  }

}
