package com.horizen.evm;

import java.math.BigInteger;

public class StateDB extends ResouceHandle {
    /**
     * Opens a view on the state at the given state root hash.
     */
    public StateDB(Database db, byte[] root) {
        super(LibEvm.stateOpen(db.handle, root));
    }

    /**
     * Close this instance and free up any native resources. Must not use this instance afterwards.
     */
    @Override
    public void close() throws Exception {
        LibEvm.stateClose(handle);
    }

    /**
     * Get current state root hash including any currently pending changes, but without committing.
     *
     * @return state root hash
     */
    public byte[] getIntermediateRoot() {
        return LibEvm.stateIntermediateRoot(handle);
    }

    /**
     * Commit any pending changes.
     *
     * @return updated state root hash
     */
    public byte[] commit() {
        return LibEvm.stateCommit(handle);
    }

    /**
     * Check if an account with the given address exists.
     *
     * @param address account address
     * @return true if account exists, otherwise false
     */
    public boolean exists(byte[] address) {
        return LibEvm.stateExists(handle, address);
    }

    /**
     * Get balance of given account.
     *
     * @param address account address
     * @return account balance, 0 if account not exist
     */
    public BigInteger getBalance(byte[] address) {
        return LibEvm.stateGetBalance(handle, address);
    }

    /**
     * Add amount to balance of given account. Will implicity create account if necessary.
     *
     * @param address account address
     * @param amount amount to add to account balance
     */
    public void addBalance(byte[] address, BigInteger amount) {
        LibEvm.stateAddBalance(handle, address, amount);
    }

    /**
     * Subtract from balance of given account.
     *
     * @param address account address
     * @param amount amount to subtract from account balance
     */
    public void subBalance(byte[] address, BigInteger amount) {
        LibEvm.stateSubBalance(handle, address, amount);
    }

    /**
     * Set balance of account balance
     *
     * @param address account address
     * @param amount amount to assign to the account balance
     */
    public void setBalance(byte[] address, BigInteger amount) {
        LibEvm.stateSetBalance(handle, address, amount);
    }

    /**
     * Get account nonce.
     *
     * @param address account address
     * @return account nonce
     */
    public BigInteger getNonce(byte[] address) {
        return LibEvm.stateGetNonce(handle, address);
    }

    /**
     * Set account nonce.
     *
     * @param address account address
     * @param nonce value to set account nonce to
     */
    public void setNonce(byte[] address, BigInteger nonce) {
        LibEvm.stateSetNonce(handle, address, nonce);
    }

    /**
     * Get account code hash.
     *
     * @param address account address
     * @return code hash
     */
    public byte[] getCodeHash(byte[] address) {
        return LibEvm.stateGetCodeHash(handle, address);
    }

    /**
     * Set account code hash, without actually adding any code.
     *
     * @param address account address
     * @param codeHash code hash to set
     */
    public void setCodeHash(byte[] address, byte[] codeHash) {
        LibEvm.stateSetCodeHash(handle, address, codeHash);
    }

    /**
     * Set code for the given account. Will also recalculate and set code hash accordingly.
     *
     * @param address account address
     * @param code code binary
     */
    public void setCode(byte[] address, byte[] code) {
        LibEvm.stateSetCode(handle, address, code);
    }

    /**
     * Read storage trie of given account.
     *
     * @param address account address
     * @param key storage key
     * @param strategy storage strategy to apply
     * @return storage value, representation depends on strategy
     */
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

    /**
     * Write to storage trie of given account.
     *
     * @param address account address
     * @param key storage key
     * @param value value to store
     * @param strategy storage strategy to apply
     */
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

    /**
     * Remove from storage trie of given account.
     *
     * @param address account address
     * @param key storage key
     * @param strategy access strategy to apply
     */
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
