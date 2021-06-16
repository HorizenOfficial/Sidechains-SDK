package com.horizen.api.http

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.certificatesubmitter.CertificateSubmitterObserver.GetProofGenerationState
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings

import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}
import java.util.{Optional => JOptional}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.horizen.api.http.SidechainDebugErrorResponse.ErrorRetrievingCertForgingState
import com.horizen.api.http.SidechainDebugRestScheme.RespCertForgingState

case class SidechainDebugApiRoute(override val settings: RESTApiSettings, certSubmitterObserverRef: ActorRef, sidechainNodeViewHolderRef: ActorRef)
                            (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute {
  override val route: Route = (pathPrefix("debug")) {
    isCertGenerationActive
  }

  def isCertGenerationActive: Route = (post & path("isCertGenerationActive")) {
    val future = certSubmitterObserverRef ? GetProofGenerationState
    val result = Await.result(future, timeout.duration).asInstanceOf[Try[Boolean]]
    result match {
      case Success(res) =>
        ApiResponseUtil.toResponse(RespCertForgingState(res))
      case Failure(e) =>
        log.error("Unable to retrieve certificate forging state.")
        ApiResponseUtil.toResponse(ErrorRetrievingCertForgingState("Unable to retrieve certificate forging state.", JOptional.of(e)))
    }
  }
}

object SidechainDebugRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCertForgingState(state: Boolean) extends SuccessResponse
}

object SidechainDebugErrorResponse {
  case class ErrorRetrievingCertForgingState(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0601"
  }
}
