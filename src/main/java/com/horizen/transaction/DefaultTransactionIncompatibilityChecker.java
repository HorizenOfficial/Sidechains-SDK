package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import com.horizen.utils.ByteArrayWrapper;

import java.util.List;

public class DefaultTransactionIncompatibilityChecker
        implements TransactionIncompatibilityChecker<BoxTransaction<Proposition, Box<Proposition>>>
{

    @Override
    public boolean isTransactionCompatible(BoxTransaction<Proposition, Box<Proposition>> newTx,
                                           BoxTransaction<Proposition, Box<Proposition>> currentTx) {
        if(newTx == null || currentTx == null)
            throw new IllegalArgumentException("Parameters can't be null.");

        // Check intersections between spent boxes of newTx and currentTxs
        // Algorithm difficulty is O(n+m), where n - number of spent boxes in newTx, m - number of currentTxs
        // Note: .boxIdsToOpen() and .unlockers() expected to be optimized (lazy calculated)
        for(BoxUnlocker unlocker : newTx.unlockers()) {
            ByteArrayWrapper closedBoxId = new ByteArrayWrapper(unlocker.closedBoxId());
            if(currentTx.boxIdsToOpen().contains(closedBoxId))
                return false;
        }
        return true;
    }

    @Override
    public boolean isTransactionCompatible(BoxTransaction<Proposition, Box<Proposition>> newTx,
                                           List<BoxTransaction<Proposition, Box<Proposition>>> currentTxs) {
        if(newTx == null || currentTxs == null)
            throw new IllegalArgumentException("Parameters can't be null.");

        // Check intersections between spent boxes of newTx and currentTxs
        // Algorithm difficulty is O(n+m), where n - number of spent boxes in newTx, m - number of currentTxs
        // Note: .boxIdsToOpen() and .unlockers() expected to be optimized (lazy calculated)
        for(BoxUnlocker unlocker : newTx.unlockers()) {
            ByteArrayWrapper closedBoxId = new ByteArrayWrapper(unlocker.closedBoxId());
            for (BoxTransaction tx : currentTxs) {
                if(tx.boxIdsToOpen().contains(closedBoxId))
                    return false;
            }
        }
        return true;
    }

    @Override
    public boolean isMemoryPoolCompatible() {
        return true;
    }
}