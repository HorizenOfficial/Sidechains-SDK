package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.ActorRegistry
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import scorex.core.api.http.ApiResponse
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

case class MainchainBlockApiRoute (override val settings: RESTApiSettings, sidechainNodeViewHolderRef: ActorRef,
                                   sidechainExtendedActorRegistry : ActorRegistry) (implicit val context: ActorRefFactory)
      extends SidechainApiRoute
      with ScorexEncoding {

  override val route : Route = (pathPrefix("mainchain"))
            {getBestMainchainBlockReferenceInfo ~ getMainchainBlockReferenceHash ~ createMainchainBlockReference}

  /**
    * It refers to the best MC block header which has already been included in a SC block. Returns:
    * 1) mc block reference hash with the most height,
    * 2)Its height in mc
    * 3)SC block ID which contains this MC block reference
    */
  def getBestMainchainBlockReferenceInfo : Route = (post & path("getBestMainchainBlockReferenceInfo"))
  {
        ApiResponse.OK
  }

  /**
    * Returns:
    * 1)MC block reference (False = Raw, True = JSON)
    * 2)Its height in MC
    * 3)SC block id which contains this MC block reference
    */
  def getMainchainBlockReferenceHash : Route = (post & path("getMainchainBlockReferenceHash"))
  {
    ApiResponse.OK
  }

  /**
    * Try to parse MC block data and create a MC block reference to be included into a SC block. Useful in combination with getblocktemplate.
    * False = raw, True = JSON
    */
  def createMainchainBlockReference : Route = (post & path("createMainchainBlockReference"))
  {
    ApiResponse.OK
  }
  
}