package com.horizen.storage;


import com.horizen.backup.BoxIterator;

public interface BoxBackupInterface {
    void backup(BoxIterator source, BackupStorage db) throws Exception;
}
