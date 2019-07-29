package com.horizen.api.http

import java.util

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.SidechainTypes
import com.horizen.box.{Box, BoxSerializer}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.proposition.Proposition
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

case class SidechainStateApi (override val settings: RESTApiSettings,
                         sidechainNodeViewHolderRef: ActorRef) (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
  extends SidechainApiRoute{


  override val route : Route = (pathPrefix("state"))
  {getClosedBoxes ~ getClosedBoxesOfType}

  private var boxCompanion : SidechainBoxesCompanion = new SidechainBoxesCompanion(new util.HashMap[java.lang.Byte, BoxSerializer[SidechainTypes#SCB]]())


  /**
    * Return all closed boxes, excluding those which ids are included in 'excludeBoxIds' list.
    */
  def getClosedBoxes : Route =
  {
    case class GetClosedBoxesRequest(excludeBoxIds: List[String] = List[String]())

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetClosedBoxesRequest](body)match {
          case Success(req) =>
            var sidechainState = sidechainNodeView.getNodeState

            var idsOfBoxesToExclude = req.excludeBoxIds.map(strId => strId.getBytes)

            var allClosedBoxes = sidechainState.getClosedBoxes(idsOfBoxesToExclude.asJava)
            var scalaClosedBoxesSerialized = allClosedBoxes.asScala.map(box => boxCompanion.toBytes(box.asInstanceOf[SidechainTypes#SCB]))

            ApiResponse("result" -> scalaClosedBoxesSerialized)

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }

  /**
    * Returns an array of JSONObjects each describing a box of a given type, excluding those which ids are included in 'excludeBoxIds' list.
    */
  def getClosedBoxesOfType : Route =
  {
    case class GetClosedBoxesOfTypeRequest(boxTypeClass: String, excludeBoxIds: List[String] = List[String]())

    entity(as[String]) { body =>
      withNodeView{ sidechainNodeView =>
        ApiInputParser.parseInput[GetClosedBoxesOfTypeRequest](body)match {
          case Success(req) =>
            var sidechainState = sidechainNodeView.getNodeState

/*            var idsOfBoxesToExclude = req.excludeBoxIds.map(strId => strId.getBytes)
            var clazz : Class[_ ] = java.lang.Class.forName(req.boxTypeClass)

            var allClosedBoxesByType = sidechainState.getClosedBoxesOfType(clazz, idsOfBoxesToExclude.asJava)
            var scalaClosedBoxesSerialized = allClosedBoxesByType.asScala.map(box => boxCompanion.toBytes(box))

            ApiResponse("result" -> scalaClosedBoxesSerialized)
 */
            ApiResponse("result" -> "")

          case Failure(exp) => ApiError(StatusCodes.BadRequest, exp.getMessage)
        }
      }
    }
  }
}
