package com.horizen.account.mempool

import com.horizen.SidechainTypes


class TxMetaInfo(val tx: SidechainTypes#SCAT, var isNotExecutable: Boolean) {
  var younger: Option[TxMetaInfo] = None
  var older: Option[TxMetaInfo] = None
}
