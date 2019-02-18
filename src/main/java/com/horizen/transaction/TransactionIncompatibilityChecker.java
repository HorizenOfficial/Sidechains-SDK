package com.horizen.transaction;

import java.util.List;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

public interface TransactionIncompatibilityChecker<T extends BoxTransaction>
{
    boolean hasIncompatibleTransactions(T newTx,
                                        List<T> currentTxs);

    boolean isMemoryPoolCompatible();
}