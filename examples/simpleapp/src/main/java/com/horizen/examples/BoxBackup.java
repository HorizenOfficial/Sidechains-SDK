package com.horizen.examples;

import com.horizen.utxo.storage.BoxBackupInterface;
import com.horizen.utxo.storage.BackupStorage;
import com.horizen.utxo.backup.BoxIterator;

public class BoxBackup implements BoxBackupInterface {
    @Override
    public void backup(BoxIterator source, BackupStorage db) throws Exception {

    }
}
