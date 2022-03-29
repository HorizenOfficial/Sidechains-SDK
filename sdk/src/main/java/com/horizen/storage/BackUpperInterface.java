package com.horizen.storage;

import com.horizen.companion.SidechainBoxesCompanion;
import org.iq80.leveldb.DBIterator;

public interface BackUpperInterface {
    void generateBackUp(DBIterator i, BackupStorage db, SidechainBoxesCompanion sbc);
}
