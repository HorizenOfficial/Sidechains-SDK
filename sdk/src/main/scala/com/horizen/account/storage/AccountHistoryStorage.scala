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
      AccountFeePaymentsInfoSerializer,
    params)
{

}
