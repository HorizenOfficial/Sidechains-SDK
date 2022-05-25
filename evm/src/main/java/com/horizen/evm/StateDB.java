package com.horizen.evm;

import com.horizen.evm.library.LibEvm;

public class StateDB implements AutoCloseable {
    private final int handle;

    public StateDB(String stateRootHex) throws Exception {
        handle = LibEvm.StateOpen(stateRootHex);
    }

    @Override
    public void close() throws Exception {
        LibEvm.StateClose(handle);
    }

    public String GetIntermediateRoot() throws Exception {
        return LibEvm.StateIntermediateRoot(handle);
    }

    public String Commit() throws Exception {
        return LibEvm.StateCommit(handle);
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
