package io.horizen.account.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.JsonNode
import io.horizen.SidechainTypes
import io.horizen.account.api.rpc.service.RpcProcessor
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.{ApiResponseUtil, SidechainApiResponse, SuccessResponse}
import io.horizen.api.http.route.SidechainApiRoute
import io.horizen.node.NodeWalletBase
import io.horizen.utils.ClosableResourceHandler
import io.prometheus.metrics.expositionformats.ExpositionFormats
import io.prometheus.metrics.model.registry.PrometheusRegistry
import sparkz.core.api.http.ApiDirectives
import io.horizen.MetricsApiSettings
import sparkz.util.SparkzLogging
import io.horizen.json.Views
import io.horizen.metrics.{MetricsHelp, MetricsManager}
import sparkz.core.settings.{ApiSettings, RESTApiSettings}

import java.io.ByteArrayOutputStream
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class AccountMetricsRoute(
      metricsSettings: MetricsApiSettings,
      sidechainNodeViewHolderRef: ActorRef
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

  override val settings = metricsSettings.asInstanceOf[ApiSettings]

  override implicit val tag: ClassTag[AccountNodeView] = ClassTag[AccountNodeView](classOf[AccountNodeView])
  override val route: Route = pathPrefix("metrics") {
     metricsHelp ~ metrics
  }

  /**
   * Returns registered metrics
   */
  def metrics: Route = get {
          entity(as[JsonNode]) { body =>
            {
              val snapshots = PrometheusRegistry.defaultRegistry.scrape
              val stream = new ByteArrayOutputStream
              ExpositionFormats.init.getPrometheusTextFormatWriter.write(stream, snapshots)
              SidechainApiResponse(stream.toString, false)
            }
      }
  }

  def metricsHelp: Route = (get & path("help")) {
    entity(as[JsonNode]) { body =>
      ApiResponseUtil.toResponse(MetricsHelpList(MetricsManager.getInstance().getHelp()))
    }
  }

  @JsonView(Array(classOf[Views.Default]))
  private[horizen] case class MetricsHelpList(helps: java.util.List[MetricsHelp])
    extends SuccessResponse


}
