package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.SidechainUtilsRestScheme._
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

case class SidechainUtilsApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute {

  override val route: Route = (pathPrefix("util")) {
    errorCodes
  }

  def dbLog: Route = (post & path("dbLog")) {
    SidechainApiResponse.OK
  }

  def setMockTime: Route = (post & path("setMockTime")) {
    SidechainApiResponse.OK
  }

  def errorCodes: Route = (post & path("errorCodes")) {
    try {
      val errorResponses: Seq[ErrorResponse] = Seq(
        MainchainErrorResponse.ErrorMainchainBlockNotFound("", None),
        MainchainErrorResponse.ErrorMainchainBlockReferenceNotFound("", None),
        MainchainErrorResponse.ErrorMainchainInvalidParameter("", None),

        SidechainBlockErrorResponse.ErrorInvalidBlockId("", None),
        SidechainBlockErrorResponse.ErrorInvalidBlockHeight("", None),
        SidechainBlockErrorResponse.ErrorBlockTemplate("", None),
        SidechainBlockErrorResponse.ErrorBlockNotAccepted("", None),
        SidechainBlockErrorResponse.ErrorBlockNotCreated("", None),

        SidechainNodeErrorResponse.ErrorInvalidHost("", None),

        SidechainTransactionErrorResponse.ErrorNotFoundTransactionId("", None),
        SidechainTransactionErrorResponse.ErrorNotFoundTransactionInput("", None),
        SidechainTransactionErrorResponse.ErrorByteTransactionParsing("", None),
        SidechainTransactionErrorResponse.GenericTransactionError("", None),

        SidechainWalletErrorResponse.ErrorSecretNotAdded("", None),
      )
      var res: Seq[SidechainApiErrorCodeSchema] = errorResponses.map(err => SidechainApiErrorCodeSchema(err.code, err.getClass.getSimpleName))
      ApiResponseUtil.toResponse(SidechainApiErrorCodeList(res.toList))
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
  }

}

object SidechainUtilsRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class SidechainApiErrorCodeList(sidechainErrorCodes: List[SidechainApiErrorCodeSchema]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class SidechainApiErrorCodeSchema(code: String, name: String)

}