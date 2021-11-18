package com.horizen.network

import akka.actor.ActorSystem.Settings
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import com.horizen.network.SidechainNodeViewSynchronizer.InternalReceivableMessages.SetSyncAsDone
import com.horizen.network.SidechainNodeViewSynchronizer.OtherNodeSyncStatus
import com.horizen.network.SidechainNodeViewSynchronizer.ReceivableMessages.GetSyncInfo
import com.typesafe.config.Config
import scorex.core.network.NetworkControllerSharedMessages.ReceivableMessages.DataFromPeer
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier


class PrioritizedViewSyncMailbox(settings: Settings, cfg: Config) extends UnboundedStablePriorityMailbox (
  PriorityGenerator {
    case DataFromPeer => 0
    case SemanticallySuccessfulModifier => 1 // or 0 ?!?!?!  it works like this in the meantime...
    case GetSyncInfo => 2
    case SetSyncAsDone => 2
    case OtherNodeSyncStatus => 2
    case _ => 100
  }

)
