package com.horizen.customconfig

import com.typesafe.config.{Config, ConfigFactory}

object CustomNodeConfiguration {

  def getCustomConfig(): Config = {

    val customConf =  ConfigFactory.parseString("""
        akka.actor.deployment {
            prio-mailbox {
                mailbox-type = "com.horizen.mailbox.PrioritizedMailbox"
            }
        }
      """)

    customConf
  }
}
