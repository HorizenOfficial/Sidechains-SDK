package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.horizen.api.ActorRegistry
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexEncoding

case class SidechainWalletApi (override val settings: RESTApiSettings,
                               sidechainNodeViewHolderRef: ActorRef,
                               sidechainExtendedActorRegistry : ActorRegistry) (implicit val context: ActorRefFactory)
      extends SidechainApiRoute {

  override val route : Route = (pathPrefix("wallet"))
            {getClosedBoxes ~ getClosedBoxesOfType ~ getBalance ~ getBalanceOfType ~ createNewPublicKeyPropositions ~ getPublicKeyPropositionByType}

  def getClosedBoxes : Route = ???

  def getClosedBoxesOfType : Route = ???

  def getBalance : Route = ???

  def getBalanceOfType : Route = ???

  def createNewPublicKeyPropositions : Route = ???

  def getPublicKeyPropositionByType : Route = ???

}
