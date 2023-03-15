package io.horizen.utxo.storage

import io.horizen.params.NetworkParams
import io.horizen.storage.{AbstractHistoryStorage, Storage}
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockSerializer}
import io.horizen.utxo.chain.{FeePaymentsInfoSerializer, SidechainFeePaymentsInfo}
import io.horizen.utxo.companion.SidechainTransactionsCompanion

class SidechainHistoryStorage(
                    storage: Storage,
                    sidechainTransactionsCompanion: SidechainTransactionsCompanion,
                    params: NetworkParams)
  extends AbstractHistoryStorage[SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage] (
                    storage,
                    new SidechainBlockSerializer(sidechainTransactionsCompanion),
                    FeePaymentsInfoSerializer,
                    params)

