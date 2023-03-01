package io.horizen.account.mempool

import io.horizen.SidechainTypes
import io.horizen.account.mempool.TxExecutableStatus.TxExecutableStatus

import scala.concurrent.duration.{Deadline, FiniteDuration}


class TxMetaInfo(val tx: SidechainTypes#SCAT,
                 var executableStatus: TxExecutableStatus,
                 txLifetime: FiniteDuration) {
  private val deadline: Deadline = txLifetime.fromNow
  var younger: Option[TxMetaInfo] = None
  var older: Option[TxMetaInfo] = None

  def hasTimedOut: Boolean = deadline.isOverdue()
}

