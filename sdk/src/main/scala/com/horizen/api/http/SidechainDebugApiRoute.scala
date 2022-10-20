package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainNodeViewHolder.InternalReceivableMessages.ReindexStep
import com.horizen.serialization.Views
import sparkz.core.settings.RESTApiSettings
import scala.concurrent.ExecutionContext


case class SidechainDebugApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef
                                   )(implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute {

  override val route: Route = pathPrefix("debug") {
     reindexStep
  }

  /**
   * Execute only one reindex step
   */
  def reindexStep: Route = (post & path("reindexStep")) {
    sidechainNodeViewHolderRef ! ReindexStep(false)
    ApiResponseUtil.toResponse(SidechainDebugRestScheme.RespReindexStep())
  }

}

object SidechainDebugRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespReindexStep() extends SuccessResponse
}


