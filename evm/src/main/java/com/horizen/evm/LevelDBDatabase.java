package com.horizen.evm;

import com.horizen.evm.params.LevelDBParams;

public class LevelDBDatabase extends Database {
    /**
     * Open a LevelDB instance in the given path.
     *
     * @param path data directory to pass to levelDB
     */
    public LevelDBDatabase(String path) {
        super(LibEvm.invoke("OpenLevelDB", new LevelDBParams(path), int.class));
    }

    @Override
    public String toString() {
        return String.format("LevelDBDatabase{handle=%d}", handle);
    }
}
