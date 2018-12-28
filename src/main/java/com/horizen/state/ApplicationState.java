package com.horizen.state;

import scorex.core.transaction.BoxTransaction;

// TO DO: provide access to HistoryReader
public interface ApplicationState {
    public boolean validate(BoxTransaction tx); // or (tx, block)

    // We should take in consideration to add a method that scans received block and eventually return a list
    // of outdated boxes (not coins boxes because coins cannot be destroyed/made them unusable arbitrarily!) to be removed
    // also if not yet opened.
    // For example this list can contains Ballots that happened in a past epoch or some other box that cannot be used anymore

    public ApplicationState applyChanges(/*changes*/); // return Try[...]

    public ApplicationState rollbackTo(String version); // return Try[...]
}



