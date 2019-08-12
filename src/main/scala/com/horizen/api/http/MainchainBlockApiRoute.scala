package com.horizen.api.http

import java.util.Optional

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.block.{MainchainBlockReferenceJSONSerializer, MainchainBlockReferenceSerializer}
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding
import scorex.util.idToBytes
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.Encoder

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class MainchainBlockApiRoute (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                  (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute
      with ScorexEncoding {

  override val route : Route = (pathPrefix("mainchain"))
            {getBestMainchainBlockReferenceInfo ~ getMainchainBlockReference ~ createMainchainBlockReference}

  /**
    * It refers to the best MC block header which has already been included in a SC block. Returns:
    * 1) Mainchain block reference hash with the most height,
    * 2) Its height in mainchain
    * 3) Sidechain block ID which contains this MC block reference
    */
  def getBestMainchainBlockReferenceInfo : Route = (post & path("getBestMainchainBlockReferenceInfo"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val history = sidechainNodeView.getNodeHistory;
        var mainchainBlockRefenrenceInfo = history.getBestMainchainBlockReferenceInfo

        ApiResponse("result" -> (
          "mainchainBlockReferenceHash" -> mainchainBlockRefenrenceInfo.getMainchainBlockReferenceHash,
          "height" -> mainchainBlockRefenrenceInfo.getHeight,
          "sidechainBlockId" -> mainchainBlockRefenrenceInfo.getSidechainBlockId
        ))
      }
    }
  }

  /**
    * Returns:
    * 1)MC block reference (False = Raw, True = JSON)
    * 2)Its height in MC
    * 3)SC block id which contains this MC block reference
    */
  def getMainchainBlockReference : Route = (post & path("getMainchainBlockReference"))
  {
    case class GetMainchainBlockReferenceRequest(mainchainBlockReferenceHash: String, format : Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            val history = sidechainNodeView.getNodeHistory

            var mcBlockRefHash = req.mainchainBlockReferenceHash.getBytes
            var format = req.format

            var mcBlockRef = history.getMainchainBlockReferenceByHash(mcBlockRefHash)
            var mcBlockHeight = history.getHeightOfMainchainBlock(mcBlockRefHash)
            var scBlock = history.getSidechainBlockByMainchainBlockReferenceHash(mcBlockRefHash)

            ApiResponse("result" -> (
              "mainchainBlockReference" ->
              {
                if(format)
                  mcBlockRef.toJson.asString.get
                else
                  MainchainBlockReferenceSerializer.toBytes(mcBlockRef).toString
              },
              "height" -> mcBlockHeight.toString,
              "sidechainBlockId" ->
                {
                  if(scBlock.isPresent)
                    idToBytes(scBlock.get().id).toString
                  else ""
                }
            ))

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Try to parse MC block data and create a MC block reference to be included into a SC block. Useful in combination with getblocktemplate.
    * False = raw, True = JSON
    */
  def createMainchainBlockReference : Route = (post & path("createMainchainBlockReference"))
  {
    case class CreateMainchainBlockReferenceRequest(mainchainBlockData: String, format : Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[CreateMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            var mcBlockData = req.mainchainBlockData.getBytes
            var format = req.format

            var optMcBlockRef = sidechainNodeView.getNodeHistory.createMainchainBlockReference(mcBlockData)
            optMcBlockRef match {
              case Success(aBlock) =>
                ApiResponse("result" ->
                  {
                    if(format)
                      aBlock.toJson.asString.get
                    else
                      MainchainBlockReferenceSerializer.toBytes(aBlock).toString
                  })
              case Failure(exp) =>
                // TO-DO Change the errorCode
                ApiResponse("error" ->
                  ("errorCode"-> 999999,
                    "errorDescription" -> s"Creation failed. ${exp.getMessage}"))
            }

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }
  
}