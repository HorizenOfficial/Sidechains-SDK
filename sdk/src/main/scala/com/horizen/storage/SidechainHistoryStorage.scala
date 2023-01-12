package com.horizen.storage

import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.chain.{FeePaymentsInfoSerializer, SidechainFeePaymentsInfo}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams

class SidechainHistoryStorage(
                    storage: Storage,
                    sidechainTransactionsCompanion: SidechainTransactionsCompanion,
                    params: NetworkParams)
  extends AbstractHistoryStorage[SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage] (
                    storage,
                    new SidechainBlockSerializer(sidechainTransactionsCompanion),
                    FeePaymentsInfoSerializer,
                    params)

