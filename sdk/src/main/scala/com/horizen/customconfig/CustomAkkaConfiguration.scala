package com.horizen.customconfig

import com.typesafe.config.{Config, ConfigFactory}

object CustomAkkaConfiguration {

  def getCustomConfig(): Config = {

   ConfigFactory.parseString("""
        akka.actor.deployment {
            prio-view-holder-mailbox {
                mailbox-type = "com.horizen.mailbox.PrioritizedViewHolderMailbox"
            }
            prio-view-sync-mailbox {
                mailbox-type = "com.horizen.network.PrioritizedViewSyncMailbox"
            }
        }
      """)
  }
}
