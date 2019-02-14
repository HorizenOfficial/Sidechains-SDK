package com.horizen.transaction;

import com.horizen.proposition.Proposition;
import com.horizen.box.Box;

import java.util.List;

@FunctionalInterface
public interface TransactionIncompatibilityChecker
{
    boolean hasIncompatibleTransactions(BoxTransaction<Proposition, Box<Proposition>> newTx,
                                        List<BoxTransaction<Proposition, Box<Proposition>>> currentTxs);
}