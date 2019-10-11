package com.horizen.api.http

import java.util.Optional

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


case class MainchainBlockApiRoute (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
                                  (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute
    with ScorexEncoding {

  override val route : Route = (pathPrefix("mainchain"))
  {getBestMainchainBlockReferenceInfo ~
    getGenesisMainchainBlockReferenceInfo ~
    getMainchainBlockReferenceInfoByHash ~
    getMainchainBlockReferenceInfoByHeight ~
    getMainchainBlockReferenceByHash}

/*  private def mainchainBlockReferenceToApiResponse(mcRef: Optional[MainchainBlockReferenceInfo], formatFlag: Boolean = true): Option[(String, Json)] = {
    (formatFlag, mcRef.asScala) match {
      case (true, Some(mainchainBlockReferenceInfo)) => Some("mainchainBlockReference" -> mainchainBlockReferenceInfo.toJson)
      case (false, Some(mainchainBlockReferenceInfo)) => Some("mainchainBlockReferenceHex" -> Json.fromString(BytesUtils.toHexString(mainchainBlockReferenceInfo.bytes())))
      case _ => None
    }
  }*/

  /**
    * It refers to the best MC block header which has already been included in a SC block. Returns:
    * 1) Mainchain block reference hash with the most height,
    * 2) Its height in mainchain
    * 3) Sidechain block ID which contains this MC block reference
    */
  def getBestMainchainBlockReferenceInfo: Route = (post & path("getBestMainchainBlockReferenceInfo"))
  {
/*    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val history = sidechainNodeView.getNodeHistory;
        val mcBlockRef = history.getBestMainchainBlockReferenceInfo
        mainchainBlockReferenceToApiResponse(mcBlockRef)
          .map(ApiResponse(_))
          .getOrElse(ApiError(StatusCodes.BadRequest, "No best block are present in the mainchain"))
      }
    }*/
    SidechainApiResponse.OK
  }


  def getGenesisMainchainBlockReferenceInfo: Route = (post & path("getGenesisMainchainBlockReferenceInfo"))
  {
/*    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        val history = sidechainNodeView.getNodeHistory;
        val mcBlockRef = history.getMainchainBlockReferenceInfoByMainchainBlockHeight(1)

        mainchainBlockReferenceToApiResponse(mcBlockRef)
          .map(ApiResponse(_))
          .getOrElse(ApiError(StatusCodes.BadRequest, "No genesis mainchain block is present"))
      }
    }*/
    SidechainApiResponse.OK
  }

  def getMainchainBlockReferenceInfoByHash : Route = (post & path("getMainchainBlockReferenceInfoByHash"))
  {
    case class GetMainchainBlockReferenceRequest(mainchainBlockReferenceHash: String, format: Boolean = false)

 /*   entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            val history = sidechainNodeView.getNodeHistory

            val mcBlockRefHash = req.mainchainBlockReferenceHash.getBytes
            val format = req.format

            val mcBlockRef = history.getMainchainBlockReferenceInfoByHash(mcBlockRefHash)
            mainchainBlockReferenceToApiResponse(mcBlockRef, format)
              .map(ApiResponse(_))
              .getOrElse(ApiError(StatusCodes.BadRequest, "No reference info had been found for given hash"))

          case Failure(exp) =>
            ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }*/
    SidechainApiResponse.OK
  }

  def getMainchainBlockReferenceInfoByHeight : Route = (post & path("getMainchainBlockReferenceInfoByHeight"))
  {
    case class GetMainchainBlockReferenceRequest(mainchainBlockReferenceHeight: Integer, format: Boolean = false)

/*    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            val history = sidechainNodeView.getNodeHistory

            val height = req.mainchainBlockReferenceHeight
            val format = req.format

            val mcBlockRef = history.getMainchainBlockReferenceInfoByMainchainBlockHeight(height)
            mainchainBlockReferenceToApiResponse(mcBlockRef, format)
              .map(ApiResponse(_))
              .getOrElse(ApiError(StatusCodes.BadRequest, "No reference info had been found for given hash"))

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }*/
    SidechainApiResponse.OK
  }


  /**
    * Returns:
    * 1)MC block reference (False = Raw, True = JSON)
    * 2)Its height in MC
    * 3)SC block id which contains this MC block reference
    */
  def getMainchainBlockReferenceByHash: Route = (post & path("getMainchainBlockReferenceByHash"))
  {
    case class GetMainchainBlockReferenceRequest(mainchainBlockReferenceHash: String, format: Boolean = false)

/*    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetMainchainBlockReferenceRequest](body)match {
          case Success(req) =>
            val history = sidechainNodeView.getNodeHistory

            val mcBlockRefHash = req.mainchainBlockReferenceHash.getBytes
            val format = req.format

            val mcBlockRef = history.getMainchainBlockReferenceByHash(mcBlockRefHash)

            (mcBlockRef.asScala, format) match {
              case (Some(ref), false) => ApiResponse("result" -> ref.bytes)
              case (Some(ref), true) => ApiResponse("result" -> ref.toJson)
              case _ =>  ApiError(StatusCodes.BadRequest, "No Mainchain reference had been found for given hash")
            }
          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }*/
    SidechainApiResponse.OK
  }
}