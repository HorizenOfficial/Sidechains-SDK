package com.horizen.evm;

import java.math.BigInteger;

public class StateDB extends ResouceHandle {
    public StateDB(Database db, byte[] root) {
        super(LibEvm.stateOpen(db.handle, root));
    }

    @Override
    public void close() throws Exception {
        LibEvm.stateClose(handle);
    }

    public byte[] getIntermediateRoot() {
        return LibEvm.stateIntermediateRoot(handle);
    }

    public byte[] commit() {
        return LibEvm.stateCommit(handle);
    }

    public boolean exists(byte[] address) {
        return LibEvm.stateExists(handle, address);
    }

    public BigInteger getBalance(byte[] address) {
        return LibEvm.stateGetBalance(handle, address);
    }

    public void addBalance(byte[] address, BigInteger amount) {
        LibEvm.stateAddBalance(handle, address, amount);
    }

    public void subBalance(byte[] address, BigInteger amount) {
        LibEvm.stateSubBalance(handle, address, amount);
    }

    public void setBalance(byte[] address, BigInteger amount) {
        LibEvm.stateSetBalance(handle, address, amount);
    }

    public BigInteger getNonce(byte[] address) {
        return LibEvm.stateGetNonce(handle, address);
    }

    public void setNonce(byte[] address, BigInteger nonce) {
        LibEvm.stateSetNonce(handle, address, nonce);
    }

    public byte[] getCodeHash(byte[] address) {
        return LibEvm.stateGetCodeHash(handle, address);
    }

    public void setCodeHash(byte[] address, byte[] codeHash) {
        LibEvm.stateSetCodeHash(handle, address, codeHash);
    }

    public void setCode(byte[] address, byte[] code) {
        LibEvm.stateSetCode(handle, address, code);
    }

    public byte[] getStorage(byte[] address, byte[] key, StateStorageStrategy strategy) {
        switch (strategy) {
            case RAW:
                return LibEvm.stateGetStorage(handle, address, key);
            case CHUNKED:
                return LibEvm.stateGetStorageBytes(handle, address, key);
            default:
                throw new RuntimeException("invalid storage strategy");
        }
    }

    public void setStorage(byte[] address, byte[] key, byte[] value, StateStorageStrategy strategy) {
        switch (strategy) {
            case RAW:
                LibEvm.stateSetStorage(handle, address, key, value);
                return;
            case CHUNKED:
                LibEvm.stateSetStorageBytes(handle, address, key, value);
                return;
            default:
                throw new RuntimeException("invalid storage strategy");
        }
    }

    public void removeStorage(byte[] address, byte[] key, StateStorageStrategy strategy) {
        switch (strategy) {
            case RAW:
                LibEvm.stateRemoveStorage(handle, address, key);
                return;
            case CHUNKED:
                LibEvm.stateRemoveStorageBytes(handle, address, key);
                return;
            default:
                throw new RuntimeException("invalid storage strategy");
        }
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
