package com.horizen.api.http.impl

import akka.actor.ActorRef
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext
import akka.actor.ActorRefFactory
import akka.http.scaladsl.server.Route
import com.horizen.api.http.SidechainApiRoute

case class SidechainUtilApi (override val settings: RESTApiSettings)(implicit val context: ActorRefFactory) extends SidechainApiRoute {

  override val route : Route = {dbLog ~ setMockTime}

  def dbLog : Route = ???
  
  def setMockTime : Route = ???
  
}