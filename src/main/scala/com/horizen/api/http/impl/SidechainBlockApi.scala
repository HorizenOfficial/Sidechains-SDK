package com.horizen.api.http.impl

import scorex.core.settings.RESTApiSettings
import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import akka.http.scaladsl.server.Route
import com.horizen.api.http.SidechainApiRouteWithFullView
import com.horizen.SidechainHistory
import com.horizen.SidechainState
import com.horizen.SidechainWallet
import com.horizen.SidechainMemoryPool
import com.horizen.api.ApiCallRequest
import scorex.core.utils.ScorexEncoding

case class SidechainBlockApiRoute (override val settings: RESTApiSettings, nodeViewHolderRef: ActorRef,
                        blocksCreatorRef: ActorRef) (implicit val context: ActorRefFactory) 
      extends SidechainApiRouteWithFullView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
      with ScorexEncoding {

  override val route : Route = {getBlock ~ getLastBlocks ~ getBlockHash ~ getBestBlockInfo ~ getBlockTemplate ~ submitBlock ~ getChainTips ~
      registerApiRoute(generate)}

  def getBlock : Route = ???
  
  def getLastBlocks : Route = ???
  
  def getBlockHash : Route = ???
  
  def getBestBlockInfo : Route = ???
  
  def getBlockTemplate : Route = ???
  
  def submitBlock : Route = ???

  @ApiCallRequest
  def getChainTips : Route = ???
  
  def generate (acr : ApiCallRequest) = ???

}