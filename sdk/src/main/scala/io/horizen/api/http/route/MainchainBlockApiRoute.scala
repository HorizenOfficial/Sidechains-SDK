package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainNodeViewBase
import com.horizen.api.http.route.MainchainErrorResponse._
import com.horizen.api.http.route.MainchainRestSchema._
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse}
import com.horizen.api.http.JacksonSupport._
import com.horizen.block.{MainchainBlockReference, SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.{AbstractFeePaymentsInfo, MainchainBlockReferenceInfo, MainchainHeaderInfo}
import com.horizen.json.Views
import com.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import com.horizen.transaction.Transaction
import com.horizen.utils.BytesUtils
import sparkz.core.settings.RESTApiSettings
import sparkz.util.SparkzEncoding

import java.util.{Optional => JOptional}
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

case class MainchainBlockApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec: ExecutionContext, override val tag: ClassTag[NV])
  extends SidechainApiRoute[TX, H, PM, FPI, NH, NS, NW, NP, NV]
    with SparkzEncoding {
  override val route: Route = pathPrefix("mainchain") {
      bestBlockReferenceInfo ~
      genesisBlockReferenceInfo ~
      blockReferenceInfoBy ~
      blockReferenceByHash ~
      mainchainHeaderInfoByHash
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

  def mainchainHeaderInfoByHash: Route = (post & path("mainchainHeaderInfoByHash")) {
    entity(as[ReqMainchainHeaderInfoBy]) { body =>
      withNodeView { sidechainNodeView =>
        sidechainNodeView.getNodeHistory.getMainchainHeaderInfoByHash(BytesUtils.fromHexString(body.hash)).asScala match {
          case Some(mcHeaderInfo) => ApiResponseUtil.toResponse(MainchainHeaderInfoResponse(mcHeaderInfo))
          case None => ApiResponseUtil.toResponse(ErrorMainchainBlockHeaderNotFound("No Mainchain Header had been found for given hash", JOptional.empty()))
        }
      }
    }
  }
}

object MainchainRestSchema {

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class MainchainBlockResponse(blockReference: MainchainBlockReference) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class MainchainBlockReferenceInfoResponse(blockReferenceInfo: MainchainBlockReferenceInfo) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class MainchainHeaderInfoResponse(mainchainHeaderInfo: MainchainHeaderInfo) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class MainchainBlockHexResponse(blockHex: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqBlockInfoBy(hash: Option[String], height: Option[Int], format: Boolean = false)

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqBlockBy(hash: String, format: Boolean = false)

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqMainchainHeaderInfoBy(hash: String)

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

  case class ErrorMainchainBlockHeaderNotFound(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0504"
  }

}