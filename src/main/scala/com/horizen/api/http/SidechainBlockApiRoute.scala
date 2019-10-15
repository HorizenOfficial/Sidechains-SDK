package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import scorex.core.settings.RESTApiSettings
import scorex.util.ModifierId
import akka.pattern.ask
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.forge.Forger.ReceivableMessages.TryGetBlockTemplate
import com.horizen.block.SidechainBlock
import com.horizen.utils.BytesUtils

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import JacksonSupport._
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.SidechainBlockErrorResponse._
import com.horizen.api.http.SidechainBlockRestSchema._
import com.horizen.serialization.Views

case class SidechainBlockApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, sidechainBlockActorRef: ActorRef, forgerRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute {

  override val route: Route = pathPrefix("block") {
    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ getBlockTemplate ~ submitBlock ~ generateBlocks
  }

  /**
    * The sidechain block by its id.
    */
  def findById: Route = (post & path("findById")) {
    entity(as[ReqFindById]) { body =>
      withNodeView { sidechainNodeView =>
        var optionSidechainBlock = sidechainNodeView.getNodeHistory.getBlockById(body.blockId)

        if (optionSidechainBlock.isPresent) {
          var sblock = optionSidechainBlock.get()
          var sblock_serialized = sblock.serializer.toBytes(sblock)
          ApiResponseUtil.toResponse(RespFindById(BytesUtils.toHexString(sblock_serialized), sblock))
        }
        else
          ApiResponseUtil.toResponse(ErrorInvalidBlockId(s"Invalid id: ${body.blockId}", None))
      }
    }
  }

  /**
    * Returns an array of number last sidechain block ids
    */
  def findLastIds: Route = (post & path("findLastIds")) {
    entity(as[ReqLastIds]) { body =>
      withNodeView { sidechainNodeView =>
        var sidechainHistory = sidechainNodeView.getNodeHistory
        var blockIds = sidechainHistory.getLastBlockIds(body.number)
        ApiResponseUtil.toResponse(RespLastIds(blockIds.asScala))
      }
    }
  }

  /**
    * Return a sidechain block Id by its height in a blockchain
    */
  def findIdByHeight: Route = (post & path("findIdByHeight")) {
    entity(as[ReqFindIdByHeight]) { body =>
      withNodeView { sidechainNodeView =>
        var sidechainHistory = sidechainNodeView.getNodeHistory
        val blockIdOptional = sidechainHistory.getBlockIdByHeight(body.height)
        if (blockIdOptional.isPresent)
          ApiResponseUtil.toResponse(RespFindIdByHeight(blockIdOptional.get()))
        else
          ApiResponseUtil.toResponse(ErrorInvalidBlockHeight(s"Invalid height: ${body.height}", None))
      }
    }
  }

  /**
    * Return here best sidechain block id and height in active chain
    */
  def getBestBlockInfo: Route = (post & path("best")) {
    withNodeView {
      sidechainNodeView =>
        val sidechainHistory = sidechainNodeView.getNodeHistory
        val height = sidechainHistory.getCurrentHeight
        if (height > 0)
          ApiResponseUtil.toResponse(RespBest(sidechainHistory.getBestBlock, height))
        else
          ApiResponseUtil.toResponse(ErrorInvalidBlockHeight(s"Invalid height: ${height}", None))
    }
  }

  /**
    * Return Sidechain block candidate for being next tip, already signed by Forger
    * Note: see todos, think about returning an unsigned block
    */
  def getBlockTemplate: Route = (post & path("template")) {
    val future = forgerRef ? TryGetBlockTemplate
    val blockTemplateTry = Await.result(future, timeout.duration).asInstanceOf[Try[SidechainBlock]]
    blockTemplateTry match {
      case Success(block) =>
        ApiResponseUtil.toResponse(RespTemplate(block, BytesUtils.toHexString(block.bytes)))
      case Failure(e) =>
        ApiResponseUtil.toResponse(ErrorBlockTemplate(s"Failed to get block template: ${e.getMessage}", None))
    }
  }

  def submitBlock: Route = (post & path("submit")) {
    entity(as[ReqSubmit]) { body =>
      withNodeView { sidechainNodeView =>
        var blockBytes: Array[Byte] = null
        Try {
          blockBytes = BytesUtils.fromHexString(body.blockHex)
        } match {
          case Success(_) =>
            val future = sidechainBlockActorRef ? SubmitSidechainBlock(blockBytes)
            val submitResultFuture = Await.result(future, timeout.duration).asInstanceOf[Future[Try[ModifierId]]]
            Await.result(submitResultFuture, timeout.duration) match {
              case Success(id) =>
                ApiResponseUtil.toResponse(RespSubmit(id))
              case Failure(e) =>
                ApiResponseUtil.toResponse(ErrorBlockNotAccepted(s"Block was not accepted: ${e.getMessage}", None))
            }
          case Failure(e) =>
            ApiResponseUtil.toResponse(ErrorBlockNotAccepted(s"Block was not accepted: ${e.getMessage}", None))
        }
      }
    }
  }

  /**
    * Returns ids of generated sidechain blocks.
    * It should automatically asks MC nodes for new blocks in order to be referenced inside the generated blocks, and assigns them automatically to
    * the newly generated blocks.
    */
  def generateBlocks: Route = (post & path("generate")) {
    entity(as[ReqGenerate]) { body =>
      withNodeView { sidechainNodeView =>
        val future = sidechainBlockActorRef ? GenerateSidechainBlocks(body.number)
        val submitResultFuture = Await.result(future, timeout.duration).asInstanceOf[Future[Try[Seq[ModifierId]]]]
        Await.result(submitResultFuture, timeout.duration) match {
          case Success(ids) =>
            ApiResponseUtil.toResponse(RespGenerate(ids.map(id => id.asInstanceOf[String])))
          case Failure(e) =>
            ApiResponseUtil.toResponse(ErrorBlockNotCreated(s"Block was not created: ${e.getMessage}", None))
        }
      }
    }
  }

}


object SidechainBlockRestSchema {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqFindById(blockId: String) {
    require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespFindById(blockHex: String, block: SidechainBlock) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqLastIds(number: Int) {
    require(number > 0, s"Invalid number $number. Number must be > 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespLastIds(lastBlockIds: Seq[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqFindIdByHeight(height: Int) {
    require(height > 0, s"Invalid height $height. Height must be > 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespFindIdByHeight(blockId: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespBest(block: SidechainBlock, height: Int) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespTemplate(block: SidechainBlock, blockHex: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqSubmit(blockHex: String) {
    require(blockHex.nonEmpty, s"Invalid hex data $blockHex. String must be not empty")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespSubmit(blockId: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGenerate(number: Int) {
    require(number > 0, s"Invalid number $number. Number must be > 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGenerate(blockIds: Seq[String]) extends SuccessResponse

}

object SidechainBlockErrorResponse {

  case class ErrorInvalidBlockId(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0101"
  }

  case class ErrorInvalidBlockHeight(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0102"
  }

  case class ErrorBlockTemplate(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0103"
  }

  case class ErrorBlockNotAccepted(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0104"
  }

  case class ErrorBlockNotCreated(description: String, exception: Option[Throwable]) extends ErrorResponse {
    override val code: String = "0105"
  }

}