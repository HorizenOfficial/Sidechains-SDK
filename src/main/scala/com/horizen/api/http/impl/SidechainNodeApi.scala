package com.horizen.api.http.impl

import akka.actor.ActorRef
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext
import akka.actor.ActorRefFactory
import akka.http.scaladsl.server.Route
import com.horizen.api.http.SidechainApiRoute

case class SidechainNodeApi (peerManager: ActorRef,
                         networkController: ActorRef,
                         override val settings: RESTApiSettings)
                        (implicit val context: ActorRefFactory, val ec: ExecutionContext) extends SidechainApiRoute {

  override val route : Route = {connect ~ getAllPeersInfo ~ setupMainchainConnection ~ getMainchainConnectionInfo}

  def connect : Route = ???
  
  def getAllPeersInfo : Route = ???
  
  def setupMainchainConnection : Route = ???
  
  def getMainchainConnectionInfo : Route = ???
  
}