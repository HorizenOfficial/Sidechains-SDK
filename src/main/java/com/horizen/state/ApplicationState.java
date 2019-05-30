package com.horizen.state;

import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.utils.ByteArrayWrapper;

import java.util.List;

// TO DO: provide access to HistoryReader
public interface ApplicationState {
    
    boolean validate(SidechainBlock block); // or (tx, block)

    // We should take in consideration to add a method that scans received block and eventually return a list
    // of outdated boxes (not coins boxes because coins cannot be destroyed/made them unusable arbitrarily!) to be removed
    // also if not yet opened.
    // For example this list can contains Ballots that happened in a past epoch or some other box that cannot be used anymore

    void onApplyChanges(List<Box> newBoxes, List<ByteArrayWrapper> boxIdsToRemove); // return Try[...]

    void onRollback(byte[] version); // return Try[...]
}



