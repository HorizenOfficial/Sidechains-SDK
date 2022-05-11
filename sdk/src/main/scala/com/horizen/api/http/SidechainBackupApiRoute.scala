package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.SidechainBackupRestScheme.RespSidechainBlockIdForBackup
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings
import com.horizen.api.http.SidechainBackupErrorResponse.ErrorRetrievingSidechainBlockIdForBackup
import com.horizen.utils.BytesUtils

import java.util.{Optional => JOptional}
import scala.concurrent.ExecutionContext


case class SidechainBackupApiRoute(override val settings: RESTApiSettings,
                                sidechainNodeViewHolderRef: ActorRef)
                               (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute {
  override val route: Route = pathPrefix("backup") {
    getSidechainBlockIdForBackup
  }

  /***
   * Retrieve the SidechainBlockId needed to rollback the SidechainStateStorage for the backup.
   * It's calculated by the following formula:
   * Genesis_MC_block_height + (current_epch-2) * withdrawalEpochLength -1
   */
  def getSidechainBlockIdForBackup: Route = (post & path("getSidechainBlockIdForBackup")) {
    withView { nodeView =>
      try {
        val withdrawalEpochLength = nodeView.state.params.withdrawalEpochLength
        val currentEpoch = nodeView.state.getWithdrawalEpochInfo.epoch
        val genesisMcBlockHeight = nodeView.history.getMainchainCreationBlockHeight
        val blockHeightToRollback = genesisMcBlockHeight + (currentEpoch -2) * withdrawalEpochLength - 1
        val mainchainBlockReferenceInfo = nodeView.history.getMainchainBlockReferenceInfoByMainchainBlockHeight(blockHeightToRollback).get()
        ApiResponseUtil.toResponse(RespSidechainBlockIdForBackup(BytesUtils.toHexString(mainchainBlockReferenceInfo.getMainchainReferenceDataSidechainBlockId)))
      } catch {
        case t: Throwable =>
          log.error("Failed to retrieve getSidechainBlockIdForBackup.", t.getMessage)
          ApiResponseUtil.toResponse(ErrorRetrievingSidechainBlockIdForBackup("Unexpected error during retrieving the sidechain block id to rollback.", JOptional.of(t)))
      }
    }
  }

}

object SidechainBackupRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespSidechainBlockIdForBackup(blockId: String) extends SuccessResponse
}

object SidechainBackupErrorResponse {
  case class ErrorRetrievingSidechainBlockIdForBackup(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0801"
  }
}