package com.horizen.storage

import com.horizen.block.{SidechainBlockSerializer, _}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams


class SidechainHistoryStorage(storage: Storage,
                              sidechainTransactionsCompanion: SidechainTransactionsCompanion,
                              params: NetworkParams)
  extends AbstractHistoryStorage[SidechainBlock, SidechainHistoryStorage](
      storage,
      new SidechainBlockSerializer(sidechainTransactionsCompanion),
      params)