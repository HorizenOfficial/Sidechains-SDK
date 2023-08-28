package io.horizen.examples;

import io.horizen.utxo.backup.BoxIterator;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.box.Box;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.state.ApplicationState;
import io.horizen.utxo.state.SidechainStateReader;
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import io.horizen.utxo.transaction.BoxTransaction;
import io.horizen.utils.ByteArrayWrapper;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.util.Success;
import scala.util.Try;

import java.io.File;
import java.util.*;

public class DefaultApplicationState implements ApplicationState {

    // two distinct storages are used in order to test a version misalignment during startup and the recover logic
    private VersionedLevelDbStorageAdapter appStorage1;
    private VersionedLevelDbStorageAdapter appStorage2;

    public static byte[] NULL_VERSION = new byte[32];
    public static byte[] DUMMY_KEY    = BytesUtils.fromHexString("abcd");

    public DefaultApplicationState(File storage1, File storage2, int versionToKeep) {
        appStorage1 = new VersionedLevelDbStorageAdapter(storage1, versionToKeep);
        appStorage2 = new VersionedLevelDbStorageAdapter(storage2, versionToKeep);
    }

    public Logger logger = LogManager.getLogger(io.horizen.examples.DefaultApplicationState.class);

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

        ByteArrayWrapper key   = new ByteArrayWrapper(DUMMY_KEY);
        ByteArrayWrapper value = new ByteArrayWrapper(blockId);

        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> toUpdate = Arrays.asList(new Pair<>(key, value));

        appStorage1.update(version, toUpdate, new ArrayList<>());
        appStorage2.update(version, toUpdate, new ArrayList<>());

        logger.info("Sidechain application state updated with version: " + BytesUtils.toHexString(version.data()));

        return new Success<>(this);
    }

    @Override
    public Try<ApplicationState> onRollback(byte[] blockId) {

        appStorage1.rollback(new ByteArrayWrapper(blockId));
        appStorage2.rollback(new ByteArrayWrapper(blockId));
        logger.info(String.format("Application state has been rollback to version: '%s'", BytesUtils.toHexString(blockId)));

        return new Success<>(this);
    }

    @Override
    public boolean checkStoragesVersion(byte[] blockId)
    {
        byte[] ver1 = appStorage1.lastVersionID().orElse(new ByteArrayWrapper(NULL_VERSION)).data();
        byte[] ver2 = appStorage2.lastVersionID().orElse(new ByteArrayWrapper(NULL_VERSION)).data();

        logger.debug(String.format("Reference version: '%s', application state current versions: '%s', '%s'",
                BytesUtils.toHexString(blockId),
                BytesUtils.toHexString(ver1),
                BytesUtils.toHexString(ver2))
        );

        return Arrays.equals(blockId, ver1) && Arrays.equals(blockId, ver2);
    }

    public void closeStorages() {
        logger.debug("Closing storages");
        appStorage1.close();
        appStorage2.close();
    }


    public Try<ApplicationState> onBackupRestore(BoxIterator i) {
        return new Success<>(this);
    }
}
