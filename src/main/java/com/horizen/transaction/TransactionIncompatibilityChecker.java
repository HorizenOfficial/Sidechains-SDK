package com.horizen.transaction;

import java.util.ArrayList;

interface TransactionIncompatibilityChecker<T extends BoxTransaction>
{
    boolean hasIncompatibleTransactions(T newTx, ArrayList<BoxTransaction> currentTxs);
}