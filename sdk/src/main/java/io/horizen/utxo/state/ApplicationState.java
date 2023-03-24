package io.horizen.utxo.state;

import io.horizen.utxo.backup.BoxIterator;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.box.Box;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.transaction.BoxTransaction;

import java.util.List;

import scala.util.Try;

// TO DO: provide access to HistoryReader
public interface ApplicationState {

    void validate(SidechainStateReader stateReader, SidechainBlock block) throws IllegalArgumentException;

    void validate(SidechainStateReader stateReader, BoxTransaction<Proposition, Box<Proposition>> transaction) throws IllegalArgumentException;

    // We should take in consideration to add a method that scans received block and eventually return a list
    // of outdated boxes (not coins boxes because coins cannot be destroyed/made them unusable arbitrarily!) to be removed
    // also if not yet opened.
    // For example this list can contains Ballots that happened in a past epoch or some other box that cannot be used anymore

    Try<ApplicationState> onApplyChanges(SidechainStateReader stateReader, byte[] blockId, List<Box<Proposition>> newBoxes, List<byte[]> boxIdsToRemove);

    Try<ApplicationState> onRollback(byte[] blockId); // return Try[...]

    // check that all storages of the application which are update by the sdk core, have the version corresponding to the
    // blockId given. This is useful when checking the alignment of the storages versions at node restart
    boolean checkStoragesVersion(byte[] blockId);

    Try<ApplicationState> onBackupRestore(BoxIterator i);
}



