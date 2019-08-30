package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer}
import com.horizen.params.NetworkParams
import io.circe.generic.auto._
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class MainchainBlockApiRoute (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
                                  (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
      extends SidechainApiRoute
      with ScorexEncoding {

  override val route : Route = (pathPrefix("mainchain"))
            {getBestMainchainBlockReferenceInfo ~
              getGenesisMainchainBlockReferenceInfo ~
              getMainchainBlockReferenceInfoByHash ~ //string -> bytes utils from hex string
              getMainchainBlockReferenceInfoByHeight ~
              getMainchainBlockReferenceByHash ~
              createMainchainBlockReference}

  /**
    * It refers to the best MC block header which has already been included in a SC block. Returns:
    * 1) Mainchain block reference hash with the most height,
    * 2) Its height in mainchain
    * 3) Sidechain block ID which contains this MC block reference
    */
  def getBestMainchainBlockReferenceInfo: Route = (post & path("getBestMainchainBlockReferenceInfo"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val history = sidechainNodeView.getNodeHistory;
        val mainchainBlockRefenrenceInfo = history.getBestMainchainBlockReferenceInfo

        ApiResponse("result" -> (
          "mainchainBlockReferenceHash" -> mainchainBlockRefenrenceInfo.get().getMainchainBlockReferenceHash,
          "height" -> mainchainBlockRefenrenceInfo.get().getHeight,
          "sidechainBlockId" -> mainchainBlockRefenrenceInfo.get().getSidechainBlockId
        ))
      }
    }
  }


  def getGenesisMainchainBlockReferenceInfo: Route = (post & path("getGenesisMainchainBlockReferenceInfo"))
  {
    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val history = sidechainNodeView.getNodeHistory;
        val mainchainBlockRefenrenceInfo = history.getMainchainBlockReferenceInfoByMainchainBlockReferenceInfoHeight(1)

        // @TODO chane after serialization implementation
        ApiResponse("result" -> (
          "mainchainBlockReferenceHash" -> mainchainBlockRefenrenceInfo.get().getMainchainBlockReferenceHash,
          "height" -> mainchainBlockRefenrenceInfo.get().getHeight,
          "sidechainBlockId" -> mainchainBlockRefenrenceInfo.get().getSidechainBlockId,
          "mainchainParent" -> mainchainBlockRefenrenceInfo.get().getParentMainchainBlockReferenceHash
        ))
      }
    }
  }


  def getMainchainBlockReferenceInfoByHash : Route = (post & path("getMainchainBlockReferenceInfoByHash"))
  {
    case class GetMainchainBlockReferenceRequest(mainchainBlockReferenceHash: String, format : Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            val history = sidechainNodeView.getNodeHistory

            val mcBlockRefHash = req.mainchainBlockReferenceHash.getBytes
            val format = req.format

            val mcBlockRefInfo = history.getMainchainBlockReferenceInfoByHash(mcBlockRefHash)

            val response = if (format) "mcBlockRefInfo.toJson" else "MainchainBlockReferenceInfoSerializer.toBytes(mcBlockRefInfo).toString"

            ApiResponse("result" -> response)

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  def getMainchainBlockReferenceInfoByHeight : Route = (post & path("getMainchainBlockReferenceInfoByHeight"))
  {
    case class GetMainchainBlockReferenceRequest(mainchainBlockReferenceHash: Integer, format: Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            val history = sidechainNodeView.getNodeHistory

            val height = req.mainchainBlockReferenceHash
            val format = req.format

            val mcBlockRef = history.getMainchainBlockReferenceInfoByMainchainBlockReferenceInfoHeight(height)

            val response = if (format) "mcBlockRef.toJson" else "MainchainBlockReferenceInfoSerializer.toBytes(mcBlockRef).toString"

            ApiResponse("result" -> response)

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
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
  def getMainchainBlockReferenceByHash : Route = (post & path("getMainchainBlockReferenceByHash"))
  {
    case class GetMainchainBlockReferenceRequest(mainchainBlockReferenceHash: String, format : Boolean = false)

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            val history = sidechainNodeView.getNodeHistory

            val mcBlockRefHash = req.mainchainBlockReferenceHash.getBytes
            val format = req.format

            val mcBlockRef = history.getMainchainBlockReferenceByHash(mcBlockRefHash)

            val response = if (format) "mcBlockRef.toJson" else "MainchainBlockReferenceInfoSerializer.toBytes(mcBlockRef).toString"

            ApiResponse("result" -> response)

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

            var optMcBlockRef =  MainchainBlockReference.create(mcBlockData, params)
            optMcBlockRef match {
              case Success(aBlock) =>
                ApiResponse("result" ->
                  {
                    if(format)
                      aBlock.json.asString.get
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