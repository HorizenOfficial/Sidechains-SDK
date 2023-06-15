package io.horizen.utxo.transaction;

import io.horizen.utils.ByteArrayWrapper;
import io.horizen.utxo.box.BoxUnlocker;

import java.util.List;

public class DefaultTransactionIncompatibilityChecker
    implements TransactionIncompatibilityChecker
{

    @Override
    public <T extends BoxTransaction> boolean isTransactionCompatible(T newTx,
                                                                      List<T> currentTxs) {
        if(newTx == null || currentTxs == null)
            throw new IllegalArgumentException("Parameters can't be null.");

        // Check intersections between spent boxes of newTx and currentTxs
        // Algorithm difficulty is O(n+m), where n - number of spent boxes in newTx, m - number of currentTxs
        // Note: .boxIdsToOpen() and .unlockers() expected to be optimized (lazy calculated)
        for(BoxUnlocker unlocker : (List<BoxUnlocker>)newTx.unlockers()) {
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