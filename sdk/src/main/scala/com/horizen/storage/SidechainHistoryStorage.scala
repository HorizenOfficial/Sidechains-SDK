package com.horizen.storage

import com.horizen.block.{SidechainBlockSerializer, _}
import com.horizen.chain.{FeePaymentsInfoSerializer, SidechainFeePaymentsInfo}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import scorex.util.ModifierId

import scala.compat.java8.OptionConverters.RichOptionalGeneric


class SidechainHistoryStorage(storage: Storage,
                              sidechainTransactionsCompanion: SidechainTransactionsCompanion,
                              params: NetworkParams)
  extends AbstractHistoryStorage[SidechainBlock, SidechainFeePaymentsInfo, SidechainHistoryStorage](
      storage,
      new SidechainBlockSerializer(sidechainTransactionsCompanion),
        FeePaymentsInfoSerializer,
      params)
{

}