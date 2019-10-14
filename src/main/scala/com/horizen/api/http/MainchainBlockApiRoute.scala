package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.{SerializationUtil, Views}
import com.horizen.utils.BytesUtils
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import JacksonSupport._
import com.horizen.api.http.Schema._
import com.horizen.node.util.MainchainBlockReferenceInfo

case class MainchainBlockApiRoute(override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef)
                                 (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute
    with ScorexEncoding {

  override val route: Route = (pathPrefix("mainchain")) {
    bestBlockInfo ~
      genesisBlockInfo ~
      blockInfoBy ~
      blockByHash
  }

  /**
    * It refers to the best MC block header which has already been included in a SC block. Returns:
    * 1) Mainchain block reference hash with the most height,
    * 2) Its height in mainchain
    * 3) Sidechain block ID which contains this MC block reference
    */
  def bestBlockInfo: Route = (post & path("bestBlockInfo")) {
    withNodeView { sidechainNodeView =>
      sidechainNodeView.getNodeHistory
        .getBestMainchainBlockReferenceInfo.asScala match {
        case Some(mcBlockRef) => SidechainApiResponse(SerializationUtil.serializeWithResult(MainchainApiResponse(Some(mcBlockRef))))
        case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult("", "No best block are present in the mainchain", ""))
      }
    }
  }


  def genesisBlockInfo: Route = (post & path("genesisBlockInfo")) {
    withNodeView { sidechainNodeView =>
      sidechainNodeView.getNodeHistory
        .getMainchainBlockReferenceInfoByMainchainBlockHeight(1).asScala match {
        case Some(mcBlockRef) => SidechainApiResponse(SerializationUtil.serializeWithResult(MainchainApiResponse(Some(mcBlockRef))))
        case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult("", "No genesis mainchain block is present", ""))
      }
    }
  }

  def blockInfoBy: Route = (post & path("blockInfoBy")) {
    entity(as[ReqBlockInfoBy]) { body =>
      withNodeView { sidechainNodeView =>
        body.hash match {
          case Some(mcBlockRefHash) =>
            sidechainNodeView.getNodeHistory
              .getMainchainBlockReferenceInfoByHash(mcBlockRefHash.getBytes).asScala match {
              case Some(mcBlockRef) =>
                if (body.format)
                  SidechainApiResponse(SerializationUtil.serializeWithResult(mcBlockRef))
                else SidechainApiResponse(SerializationUtil.serializeWithResult(MainchainApiResponse(None, Some(BytesUtils.toHexString(mcBlockRef.bytes())))
                ))
              case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult("", "No reference info had been found for given hash", ""))
            }
          case None =>
            body.height match {
              case Some(h) =>
                sidechainNodeView.getNodeHistory.getMainchainBlockReferenceInfoByMainchainBlockHeight(h).asScala match {
                  case Some(mcBlockRef) =>
                    if (body.format)
                      SidechainApiResponse(SerializationUtil.serializeWithResult(mcBlockRef))
                    else SidechainApiResponse(SerializationUtil.serializeWithResult(MainchainApiResponse(None, Some(BytesUtils.toHexString(mcBlockRef.bytes())))
                    ))
                  case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult("", "No reference info had been found for given height", ""))
                }
              case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult("", "Provide parameters either hash or height.", ""))
            }
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
  def blockByHash: Route = (post & path("blockByHash")) {
    entity(as[ReqBlockBy]) { body =>
      withNodeView { sidechainNodeView =>
        sidechainNodeView.getNodeHistory.getMainchainBlockReferenceByHash(body.hash.getBytes).asScala match {
          case Some(mcBlockRef) =>
            if (body.format)
              SidechainApiResponse(SerializationUtil.serializeWithResult(mcBlockRef))
            else SidechainApiResponse(SerializationUtil.serializeWithResult(MainchainApiResponse(None, Some(BytesUtils.toHexString(mcBlockRef.bytes)))
            ))
          case None => SidechainApiResponse(SerializationUtil.serializeErrorWithResult("", "No Mainchain reference had been found for given hash", ""))
        }
      }
    }
  }
}

object Schema {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class MainchainApiResponse(blockInfo: Option[MainchainBlockReferenceInfo], blockHex: Option[String] = None)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqBlockInfoBy(hash: Option[String], height: Option[Integer], format: Boolean = false)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqBlockBy(hash: String, format: Boolean = false)

}