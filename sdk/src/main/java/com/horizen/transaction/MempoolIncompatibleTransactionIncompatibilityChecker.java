package com.horizen.transaction;

import java.util.List;

public class MempoolIncompatibleTransactionIncompatibilityChecker implements TransactionIncompatibilityChecker
{
    private static MempoolIncompatibleTransactionIncompatibilityChecker checker;

    static {
        checker = new MempoolIncompatibleTransactionIncompatibilityChecker();
    }

    private MempoolIncompatibleTransactionIncompatibilityChecker() {

    }

    public static MempoolIncompatibleTransactionIncompatibilityChecker getChecker() {
        return checker;
    }

    @Override
    public <T extends BoxTransaction> boolean isTransactionCompatible(T newTx, List<T> currentTxs) {
        return false;
    }

    @Override
    public boolean isMemoryPoolCompatible() {
        return false;
    }
}
