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

    public String GetAccountBalance(String address) throws Exception {
        return LibEvm.StateGetAccountBalance(handle, address);
    }

    public StateAccount GetStateAccount(String address) throws Exception {
        return LibEvm.StateGetAccount(handle, address);
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
