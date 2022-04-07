package com.horizen.examples;

import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.storage.BackUpperInterface;
import com.horizen.storage.BackupStorage;
import org.iq80.leveldb.DBIterator;

public class BackUpper implements BackUpperInterface {
    @Override
    public void generateBackUp(DBIterator i, BackupStorage db, SidechainBoxesCompanion sbc) throws Exception {

    }
}
