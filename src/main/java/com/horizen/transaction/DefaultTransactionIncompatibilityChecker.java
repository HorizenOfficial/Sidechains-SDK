package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.box.BoxUnlocker;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class DefaultTransactionIncompatibilityChecker
        implements TransactionIncompatibilityChecker<BoxTransaction<Proposition, Box<Proposition>>>
{
    @Override
    public boolean hasIncompatibleTransactions(BoxTransaction<Proposition, Box<Proposition>> newTx,
                                               List<BoxTransaction<Proposition, Box<Proposition>>> currentTxs) {
        // check intersections between spending boxes of current and txs
        // TO DO: is it optimal (take in consideration other places of currentTxs usage)?
        for(BoxUnlocker unlocker : newTx.unlockers()) {
            for(BoxTransaction tx : currentTxs) {
                ArrayList<BoxUnlocker> ctxUnlockers = (ArrayList<BoxUnlocker>)tx.unlockers();
                for(BoxUnlocker u : ctxUnlockers)
                    if(Arrays.equals(unlocker.closedBoxId(), u.closedBoxId()))
                        return true;
            }
        }
        return false;
    }
}
