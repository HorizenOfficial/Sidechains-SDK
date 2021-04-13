package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.MainchainErrorResponse._
import com.horizen.api.http.MainchainRestSchema._
import com.horizen.block.MainchainBlockReference
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import java.util.{Optional => JOptional}

case class MainchainBlockApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute
    with ScorexEncoding {

  override val route: Route = (pathPrefix("mainchain")) {
    bestBlockReferenceInfo ~
      genesisBlockReferenceInfo ~
      blockReferenceInfoBy ~
      blockReferenceByHash
  }

  /**
    * It refers to the best MC block header which has already been included in a SC block. Returns:
    * 1) Mainchain block reference hash with the most height,
    * 2) Its height in mainchain
    * 3) Sidechain block ID which contains this MC block reference
    */
  def bestBlockReferenceInfo: Route = (post & path("bestBlockReferenceInfo")) {
    withNodeView { sidechainNodeView =>
      sidechainNodeView.getNodeHistory
        .getBestMainchainBlockReferenceInfo.asScala match {
        case Some(mcBlockRef) =>
          ApiResponseUtil.toResponse(MainchainBlockReferenceInfoResponse(mcBlockRef))
        case None => ApiResponseUtil.toResponse(ErrorMainchainBlockNotFound("No best block are present in the mainchain", JOptional.empty()))
      }
    }
  }


  def genesisBlockReferenceInfo: Route = (post & path("genesisBlockReferenceInfo")) {
    withNodeView { sidechainNodeView =>
      val mainchainCreationBlockHeight = sidechainNodeView.getNodeHistory.getMainchainCreationBlockHeight
      sidechainNodeView.getNodeHistory
        .getMainchainBlockReferenceInfoByMainchainBlockHeight(mainchainCreationBlockHeight).asScala match {
        case Some(mcBlockRef) => ApiResponseUtil.toResponse(MainchainBlockReferenceInfoResponse(mcBlockRef))
        case None => ApiResponseUtil.toResponse(ErrorMainchainBlockNotFound("No genesis mainchain block is present", JOptional.empty()))
      }
    }
  }

  def blockReferenceInfoBy: Route = (post & path("blockReferenceInfoBy")) {
    entity(as[ReqBlockInfoBy]) { body =>
      withNodeView { sidechainNodeView =>
        body.hash match {
          case Some(mcBlockRefHash) =>
            sidechainNodeView.getNodeHistory
              .getMainchainBlockReferenceInfoByHash(BytesUtils.fromHexString(mcBlockRefHash)).asScala match {
              case Some(mcBlockRef) =>
                if (body.format)
                  ApiResponseUtil.toResponse(MainchainBlockReferenceInfoResponse(mcBlockRef))
                else ApiResponseUtil.toResponse(MainchainBlockHexResponse(BytesUtils.toHexString(mcBlockRef.bytes())))
              case None => ApiResponseUtil.toResponse(ErrorMainchainBlockReferenceNotFound("No reference info had been found for given hash", JOptional.empty()))
            }
          case None =>
            body.height match {
              case Some(h) =>
                sidechainNodeView.getNodeHistory.getMainchainBlockReferenceInfoByMainchainBlockHeight(h).asScala match {
                  case Some(mcBlockRef) =>
                    if (body.format)
                      ApiResponseUtil.toResponse(MainchainBlockReferenceInfoResponse(mcBlockRef))
                    else ApiResponseUtil.toResponse(MainchainBlockHexResponse(BytesUtils.toHexString(mcBlockRef.bytes())))
                  case None => ApiResponseUtil.toResponse(ErrorMainchainBlockReferenceNotFound("No reference info had been found for given height", JOptional.empty()))
                }
              case None => ApiResponseUtil.toResponse(ErrorMainchainInvalidParameter("Provide parameters either hash or height.", JOptional.empty()))
            }
        }
      }
    }
  }

  /**
    * Returns:
    * 1)MC block reference (False = Raw, True = JSON)
    * 2)Its height in MC
    * 3)SC block id which contains this MC block reference
    */
  def blockReferenceByHash: Route = (post & path("blockReferenceByHash")) {
    entity(as[ReqBlockBy]) { body =>
      withNodeView { sidechainNodeView =>
        sidechainNodeView.getNodeHistory.getMainchainBlockReferenceByHash(BytesUtils.fromHexString(body.hash)).asScala match {
          case Some(mcBlockRef) =>
            if (body.format)
              ApiResponseUtil.toResponse(MainchainBlockResponse(mcBlockRef))
            else ApiResponseUtil.toResponse(MainchainBlockHexResponse(BytesUtils.toHexString(mcBlockRef.bytes)))
          case None => ApiResponseUtil.toResponse(ErrorMainchainBlockNotFound("No Mainchain reference had been found for given hash", JOptional.empty()))
        }
      }
    }
  }
}

object MainchainRestSchema {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class MainchainBlockResponse(blockReference: MainchainBlockReference) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class MainchainBlockReferenceInfoResponse(blockReferenceInfo: MainchainBlockReferenceInfo) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class MainchainBlockHexResponse(blockHex: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqBlockInfoBy(hash: Option[String], height: Option[Int], format: Boolean = false)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqBlockBy(hash: String, format: Boolean = false)

}

object MainchainErrorResponse {

  case class ErrorMainchainBlockNotFound(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0501"
  }

  case class ErrorMainchainBlockReferenceNotFound(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0502"
  }

  case class ErrorMainchainInvalidParameter(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0503"
  }

}