package com.horizen.mailbox

import akka.actor.ActorSystem.Settings
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import com.horizen.SidechainNodeViewHolder.ReceivableMessages
import com.typesafe.config.Config
import scorex.core.NodeViewHolder.ReceivableMessages
import scorex.util.ScorexLogging

class PrioritizedMailbox (settings: Settings, cfg: Config) extends UnboundedPriorityMailbox (
  PriorityGenerator {
    case scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView => 0 // internal calls must go first
    case com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView => 1 // api calls
    case _ => 100
  }

)
