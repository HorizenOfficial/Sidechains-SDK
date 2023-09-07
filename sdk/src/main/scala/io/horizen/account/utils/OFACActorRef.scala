package io.horizen.account.utils

import akka.actor.{ActorRef, ActorSystem, Props}
import io.horizen.{SidechainAppEvents, SidechainSettings}

import scala.concurrent.ExecutionContext

object OFACActorRef {
  def props(settings: SidechainSettings)
           (implicit ec: ExecutionContext): Props = {
    Props(new OFACActor(settings))
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox")
  }

  def apply(settings: SidechainSettings)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings))
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref
  }

  def apply(name: String, settings: SidechainSettings)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings), name)
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref
  }
}
