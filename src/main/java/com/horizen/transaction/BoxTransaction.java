package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;

import java.util.ArrayList;

// TO DO: do we need to put fee and timestamps members inside this abstract class?
public abstract class BoxTransaction<P extends Proposition, B extends Box<P>> extends Transaction
{
    // TO DO: think about proper collection type
    public abstract ArrayList<BoxUnlocker<P>> unlockers();

    public abstract ArrayList<B> newBoxes();

    public abstract long fee();

    public abstract long timestamp();

    @Override
    public byte[] messageToSign() {
        // TO DO: return concatenation of newBoxes()[i].bytes() + unlockers()[i].closedBoxId() + timestamp + fee
        return new byte[0];
    }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new DefaultTransactionIncompatibilityChecker();
    }
}
