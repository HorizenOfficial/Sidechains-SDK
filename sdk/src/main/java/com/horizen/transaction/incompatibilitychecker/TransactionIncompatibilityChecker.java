package com.horizen.transaction.incompatibilitychecker;

import com.horizen.transaction.BoxTransaction;

import java.util.List;

public interface TransactionIncompatibilityChecker
{
    <T extends BoxTransaction> boolean isTransactionCompatible(T newTx, List<T> currentTxs);

    boolean isMemoryPoolCompatible();
}