package com.horizen.api

import akka.actor.ActorRef

import scala.collection.concurrent.TrieMap

class SidechainExtendedActorRegistry extends ActorRegistry {

  private var actorMap : TrieMap[String, (SidechainExtendedActor, ActorRef)] = TrieMap()

  override def registerExtendedActor(extendedActor: SidechainExtendedActor, extendedActorRef: ActorRef): Boolean = {
    require(extendedActor!=null && extendedActor!=null, "Parameters must be not null")
    actorMap.put(extendedActor.getName(), (extendedActor, extendedActorRef)) match {
      case Some(entry) => true
      case None => false
    }
  }

  override def retrieveActorRef(extendedActorName: String): Option[ActorRef] = {
    actorMap.get(extendedActorName) match {
      case Some(pair) => Option(pair._2)
      case None => Option.empty
    }
  }

  override def unregisterExtendedActor(extendedActor: SidechainExtendedActor): Option[SidechainExtendedActor] = {
    require(extendedActor!=null, "Parameter must be not null")
    actorMap.remove(extendedActor.getName()) match {
      case Some(pair) => Option(pair._1)
      case None => Option.empty
    }
  }

  override def retrieveExtendedActor(extendedActorName: String): Option[SidechainExtendedActor] = {
    actorMap.get(extendedActorName) match {
      case Some(pair) => Option(pair._1)
      case None => Option.empty
    }
  }

  override def isExtendedActorRegitered(extendedActor: SidechainExtendedActor): Boolean = {
    require(extendedActor!=null, "Parameter must be not null")
    actorMap.contains(extendedActor.getName())
  }
}