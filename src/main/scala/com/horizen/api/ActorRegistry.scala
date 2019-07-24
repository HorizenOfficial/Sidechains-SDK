package com.horizen.api

import akka.actor.ActorRef

trait ActorRegistry {

  def registerExtendedActor(extendedActor : SidechainExtendedActor, extendedActorRef : ActorRef) : Boolean

  def unregisterExtendedActor(extendedActor : SidechainExtendedActor) : Option[SidechainExtendedActor]

  def retrieveExtendedActor(extendedActorName : String = "") : Option[SidechainExtendedActor]

  def retrieveActorRef(extendedActorName : String = "") : Option[ActorRef]

  def isExtendedActorRegitered(extendedActor : SidechainExtendedActor) : Boolean

}
