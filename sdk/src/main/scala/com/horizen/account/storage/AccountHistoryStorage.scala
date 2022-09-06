package com.horizen.account.storage

import com.horizen.account.block.{AccountBlock, AccountBlockSerializer}
import com.horizen.account.chain.{AccountFeePaymentsInfo, AccountFeePaymentsInfoSerializer}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.storage.{AbstractHistoryStorage, Storage}
import scorex.util.ModifierId

import scala.compat.java8.OptionConverters.RichOptionalGeneric

class AccountHistoryStorage(storage: Storage,
                            sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion,
                            params: NetworkParams)
  extends AbstractHistoryStorage[AccountBlock, AccountFeePaymentsInfo, AccountHistoryStorage](
    storage,
    new AccountBlockSerializer(sidechainTransactionsCompanion),
    params)
{
    override def getFeePaymentsInfo(blockId: ModifierId): Option[AccountFeePaymentsInfo] = {
        storage.get(feePaymentsInfoKey(blockId)).asScala.flatMap(baw => AccountFeePaymentsInfoSerializer.parseBytesTry(baw.data).toOption)
    }
}
