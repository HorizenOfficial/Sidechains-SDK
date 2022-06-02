package com.horizen.helper;

import com.horizen.account.transaction.AccountTransaction;
import com.horizen.box.Box;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.Optional;
import java.util.function.BiConsumer;

public interface AccountTransactionSubmitHelper {
    void submitTransaction(AccountTransaction<Proposition, Proof<Proposition>> tx) throws IllegalArgumentException;

    void asyncSubmitTransaction(AccountTransaction<Proposition, Proof<Proposition>> tx, BiConsumer<Boolean, Optional<Throwable>> callback);
}
