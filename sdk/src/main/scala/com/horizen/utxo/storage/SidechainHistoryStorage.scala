package com.horizen.utxo.storage

import com.horizen.params.NetworkParams
import com.horizen.storage.{AbstractHistoryStorage, Storage}
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.utxo.chain.{FeePaymentsInfoSerializer, SidechainFeePaymentsInfo}
import com.horizen.utxo.companion.SidechainTransactionsCompanion

class SidechainHistoryStorage(
                    storage: Storage,
                    sidechainTransactionsCompanion: SidechainTransactionsCompanion,
                    params: NetworkParams)
  extends AbstractHistoryStorage[SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage] (
                    storage,
                    new SidechainBlockSerializer(sidechainTransactionsCompanion),
                    FeePaymentsInfoSerializer,
                    params)

