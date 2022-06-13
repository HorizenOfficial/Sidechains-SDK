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

    public boolean exists(byte[] address) throws Exception {
        return LibEvm.stateExists(handle, address);
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

    public BigInteger getNonce(byte[] address) throws Exception {
        return LibEvm.stateGetNonce(handle, address);
    }

    public void setNonce(byte[] address, BigInteger nonce) throws Exception {
        LibEvm.stateSetNonce(handle, address, nonce);
    }

    public byte[] getCodeHash(byte[] address) throws Exception {
        return LibEvm.stateGetCodeHash(handle, address);
    }

    public void setCodeHash(byte[] address, byte[] codeHash) throws Exception {
        LibEvm.stateSetCodeHash(handle, address, codeHash);
    }

    public void setCode(byte[] address, byte[] code) throws Exception {
        LibEvm.stateSetCode(handle, address, code);
    }

    public byte[] getStorage(byte[] address, byte[] key, StateStorageStrategy strategy) throws Exception {
        switch (strategy) {
            case RAW:
                return LibEvm.stateGetStorage(handle, address, key);
            case CHUNKED:
                return LibEvm.stateGetStorageBytes(handle, address, key);
            default:
                throw new Exception("invalid storage strategy");
        }
    }

    public void setStorage(byte[] address, byte[] key, byte[] value, StateStorageStrategy strategy) throws Exception {
        switch (strategy) {
            case RAW:
                LibEvm.stateSetStorage(handle, address, key, value);
                return;
            case CHUNKED:
                LibEvm.stateSetStorageBytes(handle, address, key, value);
                return;
            default:
                throw new Exception("invalid storage strategy");
        }
    }

    public void removeStorage(byte[] address, byte[] key, StateStorageStrategy strategy) throws Exception {
        switch (strategy) {
            case RAW:
                LibEvm.stateRemoveStorage(handle, address, key);
                return;
            case CHUNKED:
                LibEvm.stateRemoveStorageBytes(handle, address, key);
                return;
            default:
                throw new Exception("invalid storage strategy");
        }
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
