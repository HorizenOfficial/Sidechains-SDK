package com.horizen.transaction;

import java.util.ArrayList;

public class DefaultTransactionIncompatibilityChecker implements TransactionIncompatibilityChecker<BoxTransaction>
{
    @Override
    public boolean hasIncompatibleTransactions(BoxTransaction newTx, ArrayList<BoxTransaction> currentTxs) {
        // check intersections between spending boxes of current and txs
        return false;
    }
}
