package com.horizen.transaction;

import java.util.List;

interface TransactionIncompatibilityChecker<T extends BoxTransaction>
{
    boolean hasIncompatibleTransactions(T newTx, List<BoxTransaction> currentTxs);
}