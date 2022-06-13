package com.horizen.evm;

public class LevelDBDatabase extends Database {
    public LevelDBDatabase(String path) {
        super(LibEvm.openLevelDB(path));
    }
}
