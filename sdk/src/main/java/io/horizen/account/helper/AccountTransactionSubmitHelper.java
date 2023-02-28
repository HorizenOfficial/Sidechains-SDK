package io.horizen.account.helper;

import io.horizen.account.transaction.AccountTransaction;
import io.horizen.helper.BaseTransactionSubmitHelper;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;

import java.util.Optional;
import java.util.function.BiConsumer;

public interface AccountTransactionSubmitHelper
        extends BaseTransactionSubmitHelper<AccountTransaction<Proposition, Proof<Proposition>>> {
    void submitTransaction(AccountTransaction<Proposition, Proof<Proposition>> tx) throws IllegalArgumentException;

    void asyncSubmitTransaction(AccountTransaction<Proposition, Proof<Proposition>> tx,
                                BiConsumer<Boolean, Optional<Throwable>> callback);
}
