package com.horizen.state;

import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.utils.ByteArrayWrapper;

import java.util.List;

import scala.util.Try;

// TO DO: provide access to HistoryReader
public interface ApplicationState {

    boolean validate(SidechainStateReader stateReader, SidechainBlock block);

    boolean validate(SidechainStateReader stateReader, BoxTransaction<Proposition, Box<Proposition>> transaction);

    // We should take in consideration to add a method that scans received block and eventually return a list
    // of outdated boxes (not coins boxes because coins cannot be destroyed/made them unusable arbitrarily!) to be removed
    // also if not yet opened.
    // For example this list can contains Ballots that happened in a past epoch or some other box that cannot be used anymore

    Try<ApplicationState> onApplyChanges(SidechainStateReader stateReader, byte[] version, List<Box<Proposition>> newBoxes, List<byte[]> boxIdsToRemove);

    Try<ApplicationState> onRollback(byte[] version); // return Try[...]
}



