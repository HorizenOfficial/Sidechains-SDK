package io.horizen.mailbox

import akka.actor.ActorSystem.Settings
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.{ApplyBiFunctionOnNodeView, ApplyFunctionOnNodeView, LocallyGeneratedSecret}
import io.horizen.AbstractSidechainNodeViewHolder.InternalReceivableMessages.ApplyModifier
import com.typesafe.config.Config
import sparkz.core.NodeViewHolder.ReceivableMessages.{LocallyGeneratedModifier, LocallyGeneratedTransaction, ModifiersFromRemote}


class PrioritizedMailbox (settings: Settings, cfg: Config) extends UnboundedStablePriorityMailbox (
  PriorityGenerator {
    case sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView => 0 // internal calls must go first
    case LocallyGeneratedModifier => 0 //block forging is of highest priority
    case io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView => 1 // api calls
    case ApplyFunctionOnNodeView => 1
    case ApplyBiFunctionOnNodeView => 1
    case LocallyGeneratedSecret => 1
    case LocallyGeneratedTransaction => 1
    case ApplyModifier => 2
    case ModifiersFromRemote => 3

    case _ => 100
  }

)
