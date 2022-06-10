package com.horizen.customconfig

import com.typesafe.config.{Config, ConfigFactory}

object CustomAkkaConfiguration {

  def getCustomConfig(): Config = {

   ConfigFactory.parseString("""
        akka.actor.deployment {
            prio-mailbox {
                mailbox-type = "com.horizen.mailbox.PrioritizedMailbox"
            }
        }
      """)
  }
}
