package com.horizen.storage

import com.horizen.block.{SidechainBlockSerializer, _}
import com.horizen.chain.{SidechainFeePaymentsInfo, FeePaymentsInfoSerializer}
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
      params)
{
      override def getFeePaymentsInfo(blockId: ModifierId): Option[SidechainFeePaymentsInfo] = {
            storage.get(feePaymentsInfoKey(blockId)).asScala.flatMap(baw => FeePaymentsInfoSerializer.parseBytesTry(baw.data).toOption)
      }
}