package com.horizen.api

import akka.actor.{Actor, ActorRef}
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext

abstract class SidechainExtendedActor(sidechainNodeViewHolderRef : ActorRef)(implicit ec : ExecutionContext) extends Actor with ScorexLogging {

  final def getName() : String = {
    getClass.getCanonicalName
  }

}
