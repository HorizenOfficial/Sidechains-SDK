package com.horizen.api.http

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

import scala.collection.JavaConverters
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Awaitable, ExecutionContext, Future}
import scala.util.{Failure, Success}

case class SidechainBlockApiRoute (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)//, sidechainBlockActorRef: ActorRef)
                                  (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("block"))
            {getBlock ~ getLastBlockIds ~ getBlockIdByHeight ~ getBestBlockInfo ~ getBlockTemplate ~ submitBlock ~ getChainTips ~ generateBlocks}

  /**
    * The sidechain block by its id.
    */
  def getBlock : Route = (post & path("getBlock"))
  {
    case class GetBlockRequest(id: String)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBlockRequest](body)match {
          case Success(req) =>
            var optionSidechainBlock = sidechainNodeView.getNodeHistory.getBlockById(req.id)

            if(optionSidechainBlock.isPresent) {
              var sblock = optionSidechainBlock.get()
              var sblock_serialized = sblock.serializer.toBytes(sblock)
              var result = "result" -> ("block" -> sblock_serialized)
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
  def getLastBlockIds : Route = (post & path("getLastBlocks"))
  {
    case class GetLastBlocks(number: Int){
      require(number > 0, s"Invalid number $number. Number must be > 0")
    }

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetLastBlocks](body)match {
          case Success(req) =>
            var sidechainHistory = sidechainNodeView.getNodeHistory
            var seqOfModifierId = sidechainHistory.getLastBlockIds(
              sidechainHistory.getBestBlock, req.number)
            var pairOfBlockIds : Seq[(String, String)] = Seq()
            seqOfModifierId.forEach(new Consumer[String] {
              override def accept(t: String): Unit = {
                pairOfBlockIds.+:(("blockId", t))
              }
            })
            var result = "result" -> pairOfBlockIds
            ApiResponse(result)
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Return a sidechain block Id by its height in a blockchain
    */
  def getBlockIdByHeight : Route = (post & path("getBlockHash"))
  {
    case class GetBlockHashRequest(height: Int){
      require(height > 0, s"Invalid height $height. Height must be > 0")
    }
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetBlockHashRequest](body)match {
          case Success(req) =>
            var sidechainHistory = sidechainNodeView.getNodeHistory
            var height = req.height
            if(height > sidechainHistory.getCurrentHeight)
              {
                // TO-DO Change the errorCode
                ApiResponse("error" -> ("errorCode" -> 999999, "errorDescription" -> s"Invalid height: $height"))
              }
            else{
              val blockId = sidechainHistory.getBlockIdByHeight(height).orElse("")
              ApiResponse("result" -> ("blockId" -> blockId))
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Return here best sidechain block id and height in active chain
    */
  def getBestBlockInfo : Route = (post & path("getBestBlockInfo"))
  {
    withNodeView{
      sidechainNodeView =>
        var sidechainHistory = sidechainNodeView.getNodeHistory
        val height = sidechainHistory.getCurrentHeight
        var blockId: Json = Json.Null
        if(height > 0)
          blockId = idToBytes(sidechainHistory.getBestBlock.id).asJson

        ApiResponse(
          "result" -> (
            "id" -> blockId,
            "height" -> sidechainHistory.getCurrentHeight
          )
        )
    }
  }
  
  def getBlockTemplate : Route = (post & path("getBlockTemplate"))
  {
    ApiResponse.OK
  }
  
  def submitBlock : Route = (post & path("submitBlock"))
  {
    ApiResponse.OK
  }

  def getChainTips : Route = (post & path("getChainTips"))
  {
    ApiResponse.OK
  }

  /**
    * Returns ids of generated sidechain blocks.
    * It should automatically asks MC nodes for new blocks in order to be referenced inside the generated blocks, and assigns them automatically to
    * the newly generated blocks.
    */
  def generateBlocks : Route = (post & path("generate"))
  {
    case class GenerateRequest(blockCount: Int){
      require(blockCount > 0, s"Invalid number $blockCount. Number must be > 0")
    }
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GenerateRequest](body)match {
          case Success(req) =>
            // TO-DO
            val awaitable : Awaitable[Future[List[ModifierId]]] = ???
            var duration : Duration = ???
            val barrier = Await.result(awaitable, duration).asInstanceOf[Future[List[ModifierId]]]
            onComplete(barrier){
              case Success(listOfBlockIds) =>
                var seqOfPairOfBlockIds : Seq[(String, Array[Byte])] = listOfBlockIds.map(id => idToBytes(id)).map(id => ("blockId", id))
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
