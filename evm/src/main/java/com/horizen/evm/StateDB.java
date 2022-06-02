package com.horizen.evm;

import com.horizen.evm.library.EvmResult;
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

    public String GetBalance(String address) throws Exception {
        return LibEvm.StateGetBalance(handle, address);
    }

    public void AddBalance(String address, String amount) throws Exception {
        LibEvm.StateAddBalance(handle, address, amount);
    }

    public void SubBalance(String address, String amount) throws Exception {
        LibEvm.StateSubBalance(handle, address, amount);
    }

    public void SetBalance(String address, String amount) throws Exception {
        LibEvm.StateSetBalance(handle, address, amount);
    }

    public long GetNonce(String address) throws Exception {
        return LibEvm.StateGetNonce(handle, address);
    }

    public void SetNonce(String address, long nonce) throws Exception {
        LibEvm.StateSetNonce(handle, address, nonce);
    }

    public String GetCodeHash(String address) throws Exception {
        return LibEvm.StateGetCodeHash(handle, address);
    }

    public EvmResult EvmExecute(String from, String to, String value, byte[] input) throws Exception {
        return LibEvm.EvmExecute(handle, from, to, value, input);
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
