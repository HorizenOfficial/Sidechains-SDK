package com.horizen.evm;

import java.math.BigInteger;

public class StateDB implements AutoCloseable {
    final int handle;

    public StateDB(byte[] root) throws Exception {
        handle = LibEvm.stateOpen(root);
    }

    @Override
    public void close() throws Exception {
        LibEvm.stateClose(handle);
    }

    public byte[] getIntermediateRoot() throws Exception {
        return LibEvm.stateIntermediateRoot(handle);
    }

    public byte[] commit() throws Exception {
        return LibEvm.stateCommit(handle);
    }

    public BigInteger getBalance(byte[] address) throws Exception {
        return LibEvm.stateGetBalance(handle, address);
    }

    public void addBalance(byte[] address, BigInteger amount) throws Exception {
        LibEvm.stateAddBalance(handle, address, amount);
    }

    public void subBalance(byte[] address, BigInteger amount) throws Exception {
        LibEvm.stateSubBalance(handle, address, amount);
    }

    public void setBalance(byte[] address, BigInteger amount) throws Exception {
        LibEvm.stateSetBalance(handle, address, amount);
    }

    public long getNonce(byte[] address) throws Exception {
        return LibEvm.stateGetNonce(handle, address);
    }

    public void setNonce(byte[] address, long nonce) throws Exception {
        LibEvm.stateSetNonce(handle, address, nonce);
    }

    public byte[] getCodeHash(byte[] address) throws Exception {
        return LibEvm.stateGetCodeHash(handle, address);
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
