package com.horizen.examples;

import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.settings.SettingsReader;
import com.horizen.state.ApplicationState;
import com.horizen.state.SidechainStateReader;
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import com.horizen.transaction.BoxTransaction;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Array;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.hash.Blake2b256;

import java.io.File;
import java.util.*;

public class DefaultApplicationState implements ApplicationState {

    private VersionedLevelDbStorageAdapter appStorage;

    public DefaultApplicationState(File storage) {
        appStorage = new VersionedLevelDbStorageAdapter(storage);
    }

    public Logger logger = LogManager.getLogger(com.horizen.examples.DefaultApplicationState.class);

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
        ByteArrayWrapper version = new ByteArrayWrapper(blockId);

        // version can not be used as a key, reverse it just to save some key/value data
        ByteArrayWrapper key   = new ByteArrayWrapper(BytesUtils.reverseBytes(blockId));
        ByteArrayWrapper value = new ByteArrayWrapper(1);

        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> toUpdate = Arrays.asList(new Pair<>(key, value));

        appStorage.update(version, toUpdate, new ArrayList<>());

        logger.info("Sidechain application state updated with version: " + BytesUtils.toHexString(version.data()));

        return new Success<>(this);
    }

    @Override
    public Try<ApplicationState> onRollback(byte[] blockId) {

        logger.info(String.format("Application state current version: '%s'", BytesUtils.toHexString(getCurrentVersion())));
        appStorage.rollback(new ByteArrayWrapper(blockId));
        logger.info(String.format("Application state has been rollback to version: '%s'", BytesUtils.toHexString(blockId)));

        return new Success<>(this);
    }

    @Override
    public byte[] getCurrentVersion() {
        Optional<ByteArrayWrapper> ver = appStorage.lastVersionID();
        if (!ver.isPresent()) {
            return new byte[32];
        }
        return ver.get().data();
    }
}
