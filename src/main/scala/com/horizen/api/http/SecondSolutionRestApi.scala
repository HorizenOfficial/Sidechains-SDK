package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.horizen.{SidechainSettings, block}
import com.horizen.block.SidechainBlock
import com.horizen.chain.SidechainBlockInfoSerializer
import io.circe.Decoder.Result
import scorex.core.api.http.{ApiError, ApiResponse}
import scorex.core.settings.RESTApiSettings
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class SecondSolutionRestApi (sidechainSettings : SidechainSettings, override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec : ExecutionContext)
  extends SidechainApiRoute {

  override def route: Route = pathPrefix("secondSolution"){resource ~ resourceWithPathParameter}

  case class ComplexRequest(
                             firstParamter : String,
                             secondParamter : Int = -1,
                             thirdParamter : Option[Boolean],
                             sidechainBlock : SidechainBlock
                           ){

  }

  case class ComplexResponse(
                              firstParamter : String,
                              secondParamter : Int,
                              thirdParamter : Boolean,
                              sidechainBlock : SidechainBlock
                            ){
  }

 implicit val decodeScBlock : Decoder[SidechainBlock] = Decoder.decodeJson.map(j => sidechainSettings.genesisBlock.get)

  def resource : Route = (post & path("resource") &
    parameters('a.as[String], 'b.as[Int].?, 'c.as[Boolean])
    ) {
    (firstParamter, secondParamter, thirdParamter) =>
      entity(as[ComplexRequest]){
        body =>
          var resp = ComplexResponse(firstParamter, secondParamter.getOrElse(-2), thirdParamter, body.sidechainBlock)
          ApiResponse(resp.firstParamter)
      }
  }

  def resourceWithPathParameter : Route = (get & pathPrefix("resourceWithPathParameter") &
    parameters('a.as[String], 'b.as[Int].?, 'c.as[Boolean])
    ){
    (firstParamter, secondParamter, thirdParamter) =>
      path(IntNumber){ int =>
        var res = Json.obj(
          ("path parameter", Json.fromInt(int)),
          ("firstParamter", Json.fromString(firstParamter)),
          ("secondParamter", Json.fromInt(secondParamter.getOrElse(-2))),
          ("thirdParamter", Json.fromBoolean(thirdParamter))
        )
        ApiResponse(res)
      }
  }

}