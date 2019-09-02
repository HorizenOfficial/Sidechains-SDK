package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import io.circe.Json
import io.circe.syntax._
import scorex.util.ModifierId
import akka.pattern.ask
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.forge.Forger.ReceivableMessages.TryGetBlockTemplate
import com.horizen.block.SidechainBlock
import com.horizen.utils.BytesUtils

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class SidechainBlockApiRoute (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, sidechainBlockActorRef: ActorRef, forgerRef: ActorRef)
                                  (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = pathPrefix("block")
            {findById ~ lastIds ~ findIdByHeight ~ getBestBlockInfo ~ getBlockTemplate ~ submitBlock  ~ generateBlocks}

  /**
    * The sidechain block by its id.
    */
  def findById : Route = (post & path("findById")) {
    case class GetBlockRequest(blockId: String) {
      require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
    }

    entity(as[GetBlockRequest]) { body =>
      withNodeView{ sidechainNodeView =>
         var optionSidechainBlock = sidechainNodeView.getNodeHistory.getBlockById(body.blockId)

            if(optionSidechainBlock.isPresent) {
              var sblock = optionSidechainBlock.get()
              var sblock_serialized = sblock.serializer.toBytes(sblock)
              SidechainApiResponse(("blockHex" -> BytesUtils.toHexString(sblock_serialized).asJson, "blockInfo" -> sblock.toJson))
            }
             else
              SidechainApiResponse(
                SidechainApiErrorResponse(
                  BlockApiGroupErrorCodes.INVALID_ID, s"Invalid id: ${body.blockId}"))
      }
    }
  }

  /**
    * Returns an array of number last sidechain block ids
    */
  def lastIds : Route = (post & path("lastIds")) {
    case class GetLastBlocks(number: Int){
      require(number > 0, s"Invalid number $number. Number must be > 0")
    }

    entity(as[GetLastBlocks]) { body =>
      withNodeView{ sidechainNodeView =>
            var sidechainHistory = sidechainNodeView.getNodeHistory
            var blockIds = sidechainHistory.getLastBlockIds(sidechainHistory.getBestBlock.id, body.number)
        SidechainApiResponse(blockIds.asScala.asJson)
      }
    }
  }

  /**
    * Return a sidechain block Id by its height in a blockchain
    */
  def findIdByHeight : Route = (post & path("findIdByHeight")) {
    case class GetBlockHashRequest(height: Int){
      require(height > 0, s"Invalid height $height. Height must be > 0")
    }
    entity(as[GetBlockHashRequest]) { body =>
      withNodeView{ sidechainNodeView =>
            var sidechainHistory = sidechainNodeView.getNodeHistory
            val blockIdOptional = sidechainHistory.getBlockIdByHeight(body.height)
            if(blockIdOptional.isPresent)
              SidechainApiResponse("id" -> blockIdOptional.get().asJson)
            else
                SidechainApiResponse(
                  SidechainApiErrorResponse(
                    BlockApiGroupErrorCodes.INVALID_HEIGHT, s"Invalid height: ${body.height}"))
      }
    }
  }

  /**
    * Return here best sidechain block id and height in active chain
    */
  def getBestBlockInfo : Route = (post & path("best")) {
    withNodeView{
      sidechainNodeView =>
        var sidechainHistory = sidechainNodeView.getNodeHistory
        val height = sidechainHistory.getCurrentHeight
        if(height > 0)
          SidechainApiResponse("blockInfo" -> sidechainHistory.getBestBlock.toJson, "height" -> height.asJson
          )
        else
          SidechainApiResponse("blockInfo" -> Json.Null, "height" -> height.asJson)
    }
  }

  /**
    * Return Sidechain block candidate for being next tip, already signed by Forger
    * Note: see todos, think about returning an unsigned block
    */
  def getBlockTemplate : Route = (post & path("template")) {
    val future = forgerRef ? TryGetBlockTemplate
    val blockTemplate = Await.result(future, timeout.duration).asInstanceOf[Try[SidechainBlock]]
    // TO DO: replace with ApiResponse(blockTemplate.toJson) in future
    blockTemplate match {
      case Success(block) =>
        SidechainApiResponse("blockHex" -> BytesUtils.toHexString(block.bytes).asJson, "blockInfo" -> block.toJson)
      case Failure(e) =>
        SidechainApiResponse(
          SidechainApiErrorResponse(
            BlockApiGroupErrorCodes.TEMPLATE_FAILURE, s"Failed to get block template: ${e.getMessage}"))
    }
  }
  
  def submitBlock : Route = (post & path("submit"))
  {
    case class GetSubmitBlockRequest(blockHex: String) {
      require(blockHex.nonEmpty, s"Invalid hex data $blockHex. String must be not empty")
    }
    entity(as[GetSubmitBlockRequest]) { body =>
      withNodeView{ sidechainNodeView =>
            var blockBytes: Array[Byte] = null
            Try {
              blockBytes = BytesUtils.fromHexString(body.blockHex)
            } match {
              case Success(_) =>
                val future = sidechainBlockActorRef ? SubmitSidechainBlock(blockBytes)
                val submitResultFuture = Await.result(future, timeout.duration).asInstanceOf[Future[Try[ModifierId]]]
                Await.result(submitResultFuture, timeout.duration) match {
                  case Success(id) =>
                    SidechainApiResponse("id" -> Json.fromString(id))
                  case Failure(e) =>
                    SidechainApiResponse(
                      SidechainApiErrorResponse(
                        BlockApiGroupErrorCodes.NOT_ACCEPTED, s"Block was not accepted: ${e.getMessage}"))
                }
              case Failure(e) =>
                SidechainApiResponse(
                  SidechainApiErrorResponse(
                    BlockApiGroupErrorCodes.NOT_ACCEPTED, s"Block was not accepted: ${e.getMessage}"))
            }
      }
    }
  }

  /**
    * Returns ids of generated sidechain blocks.
    * It should automatically asks MC nodes for new blocks in order to be referenced inside the generated blocks, and assigns them automatically to
    * the newly generated blocks.
    */
  def generateBlocks : Route = (post & path("generate"))
  {
    case class GenerateRequest(number: Int){
      require(number > 0, s"Invalid number $number. Number must be > 0")
    }
    entity(as[GenerateRequest]) { body =>
      withNodeView{ sidechainNodeView =>
            val future = sidechainBlockActorRef ? GenerateSidechainBlocks(body.number)
            val submitResultFuture = Await.result(future, timeout.duration).asInstanceOf[Future[Try[Seq[ModifierId]]]]
            Await.result(submitResultFuture, timeout.duration) match {
              case Success(ids) =>
                SidechainApiResponse("id" -> ids.map(id => id.asInstanceOf[String]).asJson)
              case Failure(e) =>
                SidechainApiResponse(
                  SidechainApiErrorResponse(
                    BlockApiGroupErrorCodes.NOT_CREATED, s"Block was not created: ${e.getMessage}"))
            }
      }
    }
  }

}
