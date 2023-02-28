package com.horizen.account.mempool

import com.horizen.SidechainTypes
import com.horizen.account.mempool.TxExecutableStatus.TxExecutableStatus


class TxMetaInfo(val tx: SidechainTypes#SCAT, var executableStatus: TxExecutableStatus) {
  var younger: Option[TxMetaInfo] = None
  var older: Option[TxMetaInfo] = None
}
