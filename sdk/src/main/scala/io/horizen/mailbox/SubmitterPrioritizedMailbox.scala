package io.horizen.mailbox

import akka.actor.ActorSystem.Settings
import com.typesafe.config.Config
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier

class SubmitterPrioritizedMailbox  (settings: Settings, cfg: Config) extends UnboundedStablePriorityMailbox (
  PriorityGenerator {
    case SemanticallySuccessfulModifier => 2

    case _ => 1
  }
)
