package com.horizen.mailbox

import akka.actor.ActorSystem.Settings
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, LocallyGeneratedSecret}
import com.typesafe.config.Config
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedTransaction


class PrioritizedMailbox (settings: Settings, cfg: Config) extends UnboundedStablePriorityMailbox (
  PriorityGenerator {
    case scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView => 0 // internal calls must go first
    case com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView => 1 // api calls
    case ApplyFunctionOnNodeView => 1
    case ApplyBiFunctionOnNodeView => 1
    case LocallyGeneratedSecret => 1
    case LocallyGeneratedTransaction => 1
    case _ => 100
  }

)
