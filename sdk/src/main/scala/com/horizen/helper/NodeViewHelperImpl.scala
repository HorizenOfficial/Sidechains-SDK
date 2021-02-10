package com.horizen.helper

import java.util.function.Consumer

import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.{Inject, ProvidedBy, Provider}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.node.SidechainNodeView
import com.horizen.SidechainApp

import scala.concurrent.duration.DurationInt

class NodeViewHelperImpl @Inject()(val appProvider: Provider[SidechainApp]) extends NodeViewHelper {

  implicit val duration: Timeout = 20 seconds


  override def getNodeView(callback: Consumer[SidechainNodeView]): Unit = {
    def f(v: SidechainNodeView) = {
      callback.accept(v)
    }
    (appProvider.get().getNodeViewActorRef() ? GetDataFromCurrentSidechainNodeView(f))
  }
}