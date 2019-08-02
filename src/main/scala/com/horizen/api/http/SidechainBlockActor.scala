package com.horizen.api.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext

class SidechainBlockActor (sidechainNodeViewHolderRef : ActorRef)(implicit ec : ExecutionContext)
  extends Actor with ScorexLogging {

  override def receive: Receive = ???
}

object SidechainBlockActor {

  object ReceivableMessages{

  }
}

object SidechainBlockActorRef{
  def props(sidechainNodeViewHolderRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainBlockActor(sidechainNodeViewHolderRef))

  def apply(sidechainNodeViewHolderRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef))
}