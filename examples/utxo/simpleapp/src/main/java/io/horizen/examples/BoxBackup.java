package io.horizen.examples;

import io.horizen.utxo.storage.BoxBackupInterface;
import io.horizen.utxo.storage.BackupStorage;
import io.horizen.utxo.backup.BoxIterator;

public class BoxBackup implements BoxBackupInterface {
    @Override
    public void backup(BoxIterator source, BackupStorage db) throws Exception {

    }
}
