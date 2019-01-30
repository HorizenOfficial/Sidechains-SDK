package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.utils.ByteArrayWrapper;

import java.util.List;

public class DefaultTransactionIncompatibilityChecker implements TransactionIncompatibilityChecker<BoxTransaction>
{
    @Override
    public boolean hasIncompatibleTransactions(BoxTransaction newTx, List<BoxTransaction> currentTxs) {
        if(newTx == null || currentTxs == null)
            throw new IllegalArgumentException("Parameters can't be null.");

        // Check intersections between spent boxes of newTx and currentTxs
        // Algorithm difficulty is O(n+m), where n - number of spent boxes in newTx, m - number of currentTxs
        // Note: .boxIdsToOpen() and .unlockers() expected to be optimized (lazy calculated)
        for(BoxUnlocker unlocker : (List<BoxUnlocker>)newTx.unlockers()) {
            for (BoxTransaction tx : currentTxs) {
                if(tx.boxIdsToOpen().contains(new ByteArrayWrapper(unlocker.closedBoxId())))
                    return true;
            }
        }
        return false;

        /* More compact, but slower solution.
        for(BoxTransaction tx : currentTxs) {
            if(Sets.intersection(tx.boxIdsToOpen(), newTx.boxIdsToOpen()).size() > 0)
                return true;
        }
        */
    }
}
