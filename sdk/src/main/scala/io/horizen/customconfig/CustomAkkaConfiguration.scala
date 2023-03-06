package io.horizen.customconfig

import com.typesafe.config.{Config, ConfigFactory}

object CustomAkkaConfiguration {

  def getCustomConfig(): Config = {

   ConfigFactory.parseString("""
        akka.actor.deployment {
            prio-mailbox {
                mailbox-type = "io.horizen.mailbox.PrioritizedMailbox"
            }
            submitter-prio-mailbox {
                mailbox-type = "io.horizen.mailbox.SubmitterPrioritizedMailbox"
            }
        }
      """)
  }
}
