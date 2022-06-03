package com.horizen.evm;

import com.horizen.evm.library.EvmResult;
import com.horizen.evm.library.LibEvm;

import java.math.BigInteger;

public class StateDB implements AutoCloseable {
    private final int handle;

    public StateDB(String stateRootHex) throws Exception {
        handle = LibEvm.stateOpen(stateRootHex);
    }

    @Override
    public void close() throws Exception {
        LibEvm.stateClose(handle);
    }

    public String getIntermediateRoot() throws Exception {
        return LibEvm.stateIntermediateRoot(handle);
    }

    public String commit() throws Exception {
        return LibEvm.stateCommit(handle);
    }

    public BigInteger getBalance(String address) throws Exception {
        return LibEvm.stateGetBalance(handle, address);
    }

    public void addBalance(String address, BigInteger amount) throws Exception {
        LibEvm.stateAddBalance(handle, address, amount);
    }

    public void subBalance(String address, BigInteger amount) throws Exception {
        LibEvm.stateSubBalance(handle, address, amount);
    }

    public void setBalance(String address, BigInteger amount) throws Exception {
        LibEvm.stateSetBalance(handle, address, amount);
    }

    public long getNonce(String address) throws Exception {
        return LibEvm.stateGetNonce(handle, address);
    }

    public void setNonce(String address, long nonce) throws Exception {
        LibEvm.stateSetNonce(handle, address, nonce);
    }

    public String getCodeHash(String address) throws Exception {
        return LibEvm.stateGetCodeHash(handle, address);
    }

    public EvmResult evmExecute(String from, String to, String value, byte[] input) throws Exception {
        return LibEvm.evmExecute(handle, from, to, value, input);
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
