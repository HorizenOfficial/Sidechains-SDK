package com.horizen.examples;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import com.horizen.wallet.ApplicationWallet;
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

    public Logger logger = LogManager.getLogger(com.horizen.examples.DefaultApplicationWallet.class);
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
    public List<byte[]> getStoragesVersionList() {
        List<byte[]> list = new ArrayList<>();

        ByteArrayWrapper ver1 = walletStorage1.lastVersionID().orElse(new ByteArrayWrapper(NULL_VERSION));
        list.add(ver1.data());

        ByteArrayWrapper ver2 = walletStorage2.lastVersionID().orElse(new ByteArrayWrapper(NULL_VERSION));
        list.add(ver2.data());

        logger.debug(String.format("Application wallet current versions: '%s', '%s'",
                BytesUtils.toHexString(ver1.data()),
                BytesUtils.toHexString(ver2.data()))
        );

        return list;
    }
}
