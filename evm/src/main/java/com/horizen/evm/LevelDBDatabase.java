package com.horizen.evm;

public class LevelDBDatabase extends Database {
    /**
     * Open a LevelDB instance in the given path.
     *
     * @param path data directory to pass to levelDB
     */
    public LevelDBDatabase(String path) {
        super(LibEvm.openLevelDB(path));
    }

    @Override
    public String toString() {
        return String.format("LevelDBDatabase{handle=%d}", handle);
    }
}
