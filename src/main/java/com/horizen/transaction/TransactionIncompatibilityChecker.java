package com.horizen.transaction;

import java.util.List;

public interface TransactionIncompatibilityChecker<T>
{
    boolean hasIncompatibleTransactions(T newTx, List<T> currentTxs);
}