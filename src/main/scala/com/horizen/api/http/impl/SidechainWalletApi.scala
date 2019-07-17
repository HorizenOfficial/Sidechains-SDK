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

case class SidechainWalletApi (override val settings: RESTApiSettings) (override val nodeViewHolderRef: ActorRef,
                        blocksCreatorRef: ActorRef) (implicit val context: ActorRefFactory) 
      extends SidechainApiRouteWithFullView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
      with ScorexEncoding {

  override val route : Route = {getClosedBoxes ~ getClosedBoxesOfType ~ getBalance ~ getBalanceOfType ~ createNewPublicKeyPropositions ~ getPublicKeyPropositionByType}

  def getClosedBoxes : Route = ???
  
  def getClosedBoxesOfType : Route = ???
  
  def getBalance : Route = ???
  
  def getBalanceOfType : Route = ???
  
  def createNewPublicKeyPropositions : Route = ???
  
  def getPublicKeyPropositionByType : Route = ???
  
}