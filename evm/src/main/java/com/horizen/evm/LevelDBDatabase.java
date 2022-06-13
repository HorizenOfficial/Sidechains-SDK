package com.horizen.evm;

public class LevelDBDatabase extends Database {
    public LevelDBDatabase(String path) throws Exception {
        super(LibEvm.openLevelDB(path));
    }
}
