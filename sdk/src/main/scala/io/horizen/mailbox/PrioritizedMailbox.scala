package io.horizen.mailbox

import akka.actor.ActorSystem.Settings
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, LocallyGeneratedSecret}
import com.horizen.AbstractSidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier
import com.typesafe.config.Config
import sparkz.core.NodeViewHolder.ReceivableMessages.{LocallyGeneratedTransaction, ModifiersFromRemote}


class PrioritizedMailbox (settings: Settings, cfg: Config) extends UnboundedStablePriorityMailbox (
  PriorityGenerator {
    case sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView => 0 // internal calls must go first
    case com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView => 1 // api calls
    case ApplyFunctionOnNodeView => 1
    case ApplyBiFunctionOnNodeView => 1
    case LocallyGeneratedSecret => 1
    case LocallyGeneratedTransaction => 1
    case ApplyModifier => 2
    case ModifiersFromRemote => 3

    case _ => 100
  }

)
