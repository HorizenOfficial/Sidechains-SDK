package io.horizen.examples;

import io.horizen.utxo.backup.BoxIterator;
import io.horizen.utxo.box.Box;
import io.horizen.proposition.Proposition;
import io.horizen.secret.Secret;
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import io.horizen.utils.ByteArrayWrapper;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.Pair;
import io.horizen.utxo.wallet.ApplicationWallet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultApplicationWallet implements ApplicationWallet {

    // two distinct storages are used in order to test a version misalignment during startup and the recover logic
    private VersionedLevelDbStorageAdapter walletStorage1;
    private VersionedLevelDbStorageAdapter walletStorage2;

    public static byte[] NULL_VERSION = new byte[32];
    public static byte[] DUMMY_KEY    = BytesUtils.fromHexString("abcd");

    public DefaultApplicationWallet(File storage1, File storage2) {
        walletStorage1 = new VersionedLevelDbStorageAdapter(storage1);
        walletStorage2 = new VersionedLevelDbStorageAdapter(storage2);
    }

    public Logger logger = LogManager.getLogger(io.horizen.examples.DefaultApplicationWallet.class);
    @Override
    public void onAddSecret(Secret secret) {

    }

    @Override
    public void onRemoveSecret(Proposition proposition) {

    }

    @Override
    public void onChangeBoxes(byte[] blockId, List<Box<Proposition>> boxesToUpdate, List<byte[]> boxIdsToRemove) {
        ByteArrayWrapper version = new ByteArrayWrapper(blockId);

        ByteArrayWrapper key   = new ByteArrayWrapper(DUMMY_KEY);
        ByteArrayWrapper value = new ByteArrayWrapper(blockId);

        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> toUpdate = Arrays.asList(new Pair<>(key, value));

        walletStorage1.update(version, toUpdate, new ArrayList<>());
        walletStorage2.update(version, toUpdate, new ArrayList<>());

        logger.info("Sidechain application wallet updated with version: " + BytesUtils.toHexString(version.data()));
    }

    @Override
    public void onRollback(byte[] blockId) {

        walletStorage1.rollback(new ByteArrayWrapper(blockId));
        walletStorage2.rollback(new ByteArrayWrapper(blockId));
        logger.info(String.format("Application wallet has been rollback to version: '%s'", BytesUtils.toHexString(blockId)));
    }

    @Override
    public boolean checkStoragesVersion(byte[] blockId)
    {
        byte[] ver1 = walletStorage1.lastVersionID().orElse(new ByteArrayWrapper(NULL_VERSION)).data();
        byte[] ver2 = walletStorage2.lastVersionID().orElse(new ByteArrayWrapper(NULL_VERSION)).data();

        logger.debug(String.format("Reference version: '%s', application wallet current versions: '%s', '%s'",
                BytesUtils.toHexString(blockId),
                BytesUtils.toHexString(ver1),
                BytesUtils.toHexString(ver2))
        );

        return Arrays.equals(blockId, ver1) && Arrays.equals(blockId, ver2);
    }

    public void closeStorages() {
        logger.debug("Closing storages");
        walletStorage1.close();
        walletStorage2.close();
    }

    @Override
    public void onBackupRestore(BoxIterator i) {

    }
}
