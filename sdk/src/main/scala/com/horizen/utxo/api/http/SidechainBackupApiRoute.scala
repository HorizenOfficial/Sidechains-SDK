package com.horizen.utxo.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.route.SidechainApiRoute
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse}
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.json.Views
import com.horizen.utils.BytesUtils
import com.horizen.utxo.api.http.SidechainBackupErrorResponse.{ErrorRetrievingSidechainBlockIdForBackup, GenericBackupApiError}
import com.horizen.utxo.api.http.SidechainBackupRestScheme.{ReqGetInitialBoxes, RespGetInitialBoxes, RespSidechainBlockIdForBackup}
import com.horizen.utxo.backup.BoxIterator
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.utxo.box.Box
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.node._
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

case class SidechainBackupApiRoute(override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef,
                                   boxIterator: BoxIterator,
                                   params: NetworkParams)
                                  (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute[
  SidechainTypes#SCBT,
  SidechainBlockHeader,
  SidechainBlock,
  SidechainFeePaymentsInfo,
  NodeHistory,
  NodeState,
  NodeWallet,
  NodeMemoryPool,
  SidechainNodeView] {

  override implicit val tag: ClassTag[SidechainNodeView] = ClassTag[SidechainNodeView](classOf[SidechainNodeView])
  override val route: Route = pathPrefix("backup") {
    getSidechainBlockIdForBackup ~ getRestoredBoxes
  }

  /** *
   * Retrieve the SidechainBlockId needed to rollback the SidechainStateStorage for the backup.
   * It's calculated by the following formula:
   * Genesis_MC_block_height + (current_epch-2) * withdrawalEpochLength -1
   */
  def getSidechainBlockIdForBackup: Route = (post & path("getSidechainBlockIdForBackup")) {
    withView { nodeView =>
      try {
        val withdrawalEpochLength = params.withdrawalEpochLength
        val currentEpoch = nodeView.state.getWithdrawalEpochInfo.epoch
        val genesisMcBlockHeight = nodeView.history.getMainchainCreationBlockHeight
        val blockHeightToRollback = genesisMcBlockHeight + (currentEpoch - 2) * withdrawalEpochLength - 1
        val mainchainBlockReferenceInfo = nodeView.history.getMainchainBlockReferenceInfoByMainchainBlockHeight(blockHeightToRollback).get()
        ApiResponseUtil.toResponse(RespSidechainBlockIdForBackup(BytesUtils.toHexString(mainchainBlockReferenceInfo.getMainchainReferenceDataSidechainBlockId)))
      } catch {
        case t: Throwable =>
          log.error("Failed to retrieve getSidechainBlockIdForBackup.", t.getMessage)
          ApiResponseUtil.toResponse(ErrorRetrievingSidechainBlockIdForBackup("Unexpected error during retrieving the sidechain block id to rollback.", JOptional.of(t)))
      }
    }
  }


  /**
   * Return the initial boxes restored in a paginated way.
   */
  def getRestoredBoxes: Route = (post & path("getRestoredBoxes")) {
    entity(as[ReqGetInitialBoxes]) { body =>
      def getBoxId: JOptional[Array[Byte]] = body.lastBoxId match {
        case Some(boxId) =>
          if (boxId.equals("")) {
            JOptional.empty()
          } else {
            JOptional.of(BytesUtils.fromHexString(boxId))
          }
        case None =>
          JOptional.empty()
      }

      Try {
        boxIterator.getNextBoxes(body.numberOfElements, getBoxId)
      } match {
        case Success(boxes) =>
          ApiResponseUtil.toResponse(RespGetInitialBoxes(boxes.asScala.toList))
        case Failure(e) =>
          ApiResponseUtil.toResponse(GenericBackupApiError("GenericBackupApiError", JOptional.of(e)))
      }
    }
  }

}

object SidechainBackupRestScheme {
  final val MAX_NUMBER_OF_BOX_REQUEST = 100

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespSidechainBlockIdForBackup(blockId: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqGetInitialBoxes(numberOfElements: Int, lastBoxId: Option[String]) {
    require(numberOfElements > 0, s"Invalid numberOfElements $numberOfElements. It should be > 0")
    require(numberOfElements <= MAX_NUMBER_OF_BOX_REQUEST, s"Invalid numberOfElements $numberOfElements. It should be <= $MAX_NUMBER_OF_BOX_REQUEST")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespGetInitialBoxes(boxes: List[Box[Proposition]]) extends SuccessResponse
}

object SidechainBackupErrorResponse {
  case class ErrorRetrievingSidechainBlockIdForBackup(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0801"
  }

  case class GenericBackupApiError(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0802"
  }
}