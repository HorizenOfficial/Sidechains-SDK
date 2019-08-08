package com.horizen.api.http

import java.util.concurrent.TimeUnit.SECONDS
import java.util.function.Consumer

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import io.circe.Json
import io.circe.syntax._
import scorex.util.{ModifierId, idToBytes}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.forge.Forger.ReceivableMessages.GetBlockTemplate
import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.utils.BytesUtils

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class SidechainBlockApiRoute (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, sidechainBlockActorRef: ActorRef, forgerRef: ActorRef)
                                  (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("block"))
            {getBlock ~ getLastBlockIds ~ getBlockIdByHeight ~ getBestBlockInfo ~ getBlockTemplate ~ submitBlock  ~ generateBlocks}

  /**
    * The sidechain block by its id.
    */
  def getBlock : Route = (post & path("getBlock")) {
    case class GetBlockRequest(id: String) {
      require(id.length == 64, s"Invalid id $id. Id length must be 64")
    }

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBlockRequest](body) match {
          case Success(req) =>
            var optionSidechainBlock = sidechainNodeView.getNodeHistory.getBlockById(req.id)

            if(optionSidechainBlock.isPresent) {
              var sblock = optionSidechainBlock.get()
              var sblock_serialized = sblock.serializer.toBytes(sblock)
              var result = "blockHex" -> BytesUtils.toHexString(sblock_serialized).asJson
              ApiResponse(result)
            }
             else{
                var result = "error" ->
                    (
                      // TO-DO Change the errorCode
                      "errorCode" -> 999999,
                      "errorDescription" -> s"Invalid id: ${req.id}"
                    )
                ApiResponse(result)
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Returns an array of number last sidechain block ids
    */
  def getLastBlockIds : Route = (post & path("getLastBlocks")) {
    case class GetLastBlocks(number: Int){
      require(number > 0, s"Invalid number $number. Number must be > 0")
    }

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetLastBlocks](body) match {
          case Success(req) =>
            var sidechainHistory = sidechainNodeView.getNodeHistory
            var blockIds = sidechainHistory.getLastBlockIds(sidechainHistory.getBestBlock.id, req.number)

            ApiResponse("result" -> blockIds.asScala.asJson)
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Return a sidechain block Id by its height in a blockchain
    */
  def getBlockIdByHeight : Route = (post & path("getBlockHash")) {
    case class GetBlockHashRequest(height: Int){
      require(height > 0, s"Invalid height $height. Height must be > 0")
    }
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBlockHashRequest](body)match {
          case Success(req) =>
            var sidechainHistory = sidechainNodeView.getNodeHistory
            val blockIdOptional = sidechainHistory.getBlockIdByHeight(req.height)
            if(blockIdOptional.isPresent)
              ApiResponse("id" -> blockIdOptional.get().asJson)
            else
              {
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> s"Invalid height: ${req.height}"))
              }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Return here best sidechain block id and height in active chain
    */
  def getBestBlockInfo : Route = (post & path("getBestBlockInfo")) {
    withNodeView{
      sidechainNodeView =>
        var sidechainHistory = sidechainNodeView.getNodeHistory
        val height = sidechainHistory.getCurrentHeight
        var blockId: Json = Json.Null
        if(height > 0)
          blockId = Json.fromString(sidechainHistory.getBestBlock.id)

        ApiResponse(
            "id" -> blockId,
            "height" -> height.asJson
        )
    }
  }

  /**
    * Return Sidechain block candidate for being next tip, signed by Forger
    * Note: see todos, think about returning an unsigned block
    */
  def getBlockTemplate : Route = (post & path("getBlockTemplate")) {
    val future = forgerRef ? GetBlockTemplate
    val blockTemplate = Await.result(future, timeout.duration).asInstanceOf[SidechainBlock]
    // TO DO: replace with ApiResponse(blockTemplate.toJson) in future
    ApiResponse("blockHex" -> BytesUtils.toHexString(blockTemplate.bytes).asJson)
  }
  
  def submitBlock : Route = (post & path("submitBlock"))
  {
    case class GetSubmitBlockRequest(blockHex: String) {
      require(blockHex.nonEmpty, s"Invalid hex data $blockHex. String must be not empty")
    }
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetSubmitBlockRequest](body) match {
          case Success(req) =>
            var blockBytes: Array[Byte] = null
            Try {
              blockBytes = BytesUtils.fromHexString(req.blockHex)
            } match {
              case Success(_) =>
                val future = sidechainBlockActorRef ? SubmitSidechainBlock(blockBytes)
                val submitResult = Await.result(future, timeout.duration).asInstanceOf[Try[ModifierId]]
                submitResult match {
                  case Success(id) => ApiResponse("id" -> Json.fromString(id))
                  case Failure(e) => ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> s"Block was not accepted: ${e.getMessage}"))
                }
              case Failure(e) =>
                ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> s"Block was not accepted1: ${e.getMessage}"))
            }

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
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
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GenerateRequest](body) match {
          case Success(req) =>
            val barrier = Await.result(
              sidechainBlockActorRef ? GenerateSidechainBlocks(req.number),
              settings.timeout).asInstanceOf[Future[List[String]]]
            onComplete(barrier){
              case Success(listOfBlockIds) =>
                var seqOfPairOfBlockIds : Seq[(String, String)] = listOfBlockIds.map(id => ("blockId", id))
                ApiResponse("result" -> seqOfPairOfBlockIds)
              case Failure(exp) =>
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> exp.getMessage))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

}
