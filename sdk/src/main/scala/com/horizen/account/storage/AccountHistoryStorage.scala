package com.horizen.account.storage

import com.horizen.account.block.{AccountBlock, AccountBlockSerializer}
import com.horizen.companion.SidechainAccountTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.storage.{AbstractHistoryStorage, Storage}

class AccountHistoryStorage(storage: Storage,
                            sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion,
                            params: NetworkParams)
  extends AbstractHistoryStorage[AccountBlock, AccountHistoryStorage](
    storage,
    new AccountBlockSerializer(sidechainTransactionsCompanion),
    params)
