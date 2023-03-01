package io.horizen.account.storage

import io.horizen.account.block.{AccountBlock, AccountBlockSerializer}
import io.horizen.account.chain.{AccountFeePaymentsInfo, AccountFeePaymentsInfoSerializer}
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.params.NetworkParams
import io.horizen.storage.{AbstractHistoryStorage, Storage}


class AccountHistoryStorage(storage: Storage,
                            sidechainTransactionsCompanion: SidechainAccountTransactionsCompanion,
                            params: NetworkParams)
  extends AbstractHistoryStorage[
    AccountBlock,
    AccountFeePaymentsInfo,
    AccountHistoryStorage](
      storage,
      new AccountBlockSerializer(sidechainTransactionsCompanion),
      AccountFeePaymentsInfoSerializer,
      params
    ) {}
