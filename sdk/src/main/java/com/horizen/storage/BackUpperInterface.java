package com.horizen.storage;

import com.horizen.companion.SidechainBoxesCompanion;

public interface BackUpperInterface {
    void generateBackUp(StorageIterator i, BackupStorage db, SidechainBoxesCompanion sbc) throws Exception;
}
