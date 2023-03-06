package io.horizen.utxo.customtypes;

import io.horizen.utxo.backup.BoxIterator;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.box.Box;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.state.ApplicationState;
import io.horizen.utxo.state.SidechainStateReader;
import io.horizen.utxo.transaction.BoxTransaction;
import scala.util.Success;
import scala.util.Try;

import java.util.List;

public class DefaultApplicationState implements ApplicationState {
    @Override
    public void validate(SidechainStateReader stateReader, SidechainBlock block) throws IllegalArgumentException {
        // do nothing always successful
    }

    @Override
    public void validate(SidechainStateReader stateReader, BoxTransaction<Proposition, Box<Proposition>> transaction) throws IllegalArgumentException {
        // do nothing always successful
    }

    @Override
    public Try<ApplicationState> onApplyChanges(SidechainStateReader stateReader, byte[] blockId, List<Box<Proposition>> newBoxes, List<byte[]> boxIdsToRemove) {
        return new Success<>(this);
    }

    @Override
    public Try<ApplicationState> onRollback(byte[] blockId) {
        return new Success<>(this);
    }

    @Override
    public boolean checkStoragesVersion(byte[] blockId) { return true; }

    @Override
    public Try<ApplicationState> onBackupRestore(BoxIterator i) { return new Success<>(this); }
}
