package com.horizen.account.helper;

import com.horizen.account.transaction.AccountTransaction;
import com.horizen.helper.BaseTransactionSubmitHelper;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;

import java.util.Optional;
import java.util.function.BiConsumer;

public interface AccountTransactionSubmitHelper
        extends BaseTransactionSubmitHelper<AccountTransaction<Proposition, Proof<Proposition>>> {
    void submitTransaction(AccountTransaction<Proposition, Proof<Proposition>> tx) throws IllegalArgumentException;

    void asyncSubmitTransaction(AccountTransaction<Proposition, Proof<Proposition>> tx,
                                BiConsumer<Boolean, Optional<Throwable>> callback);
}
