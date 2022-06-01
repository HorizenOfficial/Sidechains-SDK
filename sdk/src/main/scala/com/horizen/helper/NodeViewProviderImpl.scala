package com.horizen.helper

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentNodeView
import com.horizen.node.SidechainNodeView

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class NodeViewProviderImpl(var nodeViewActor: ActorRef) extends  NodeViewProvider {

  implicit val duration: Timeout = 20 seconds

  override def getNodeView(f: SidechainNodeView => Unit): Unit = {
    nodeViewActor ?  GetDataFromCurrentNodeView(f)
  }
}

