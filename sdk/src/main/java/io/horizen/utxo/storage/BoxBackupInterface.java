package io.horizen.utxo.storage;


import io.horizen.utxo.backup.BoxIterator;

public interface BoxBackupInterface {
    void backup(BoxIterator source, BackupStorage db) throws Exception;
}
