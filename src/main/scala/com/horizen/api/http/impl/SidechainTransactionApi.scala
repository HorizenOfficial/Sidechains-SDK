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
import scorex.core.utils.ScorexEncoding

case class SidechainTransactionApi (override val settings: RESTApiSettings, nodeViewHolderRef: ActorRef,
                        blocksCreatorRef: ActorRef) (implicit val context: ActorRefFactory) 
      extends SidechainApiRouteWithFullView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
      with ScorexEncoding {

  override val route : Route = {getMemoryPool ~ getTransaction ~ decodeRawTransaction ~ createRegularTransaction ~ sendCoinsToAddress ~ sendRawTransaction}

  def getMemoryPool : Route = ???
  
  def getTransaction : Route = ???
  
  def decodeRawTransaction : Route = ???
  
  def createRegularTransaction : Route = ???
  
  def sendCoinsToAddress : Route = ???
  
  def sendRawTransaction : Route = ???
  
}