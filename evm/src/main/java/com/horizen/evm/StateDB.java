package com.horizen.evm;

import com.horizen.evm.interop.*;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

import java.math.BigInteger;

public class StateDB extends ResourceHandle {
    /**
     * Code hash of an empty byte array
     */
    public static final Hash EMPTY_CODE_HASH = new Hash(
        "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");

    /**
     * TrieHasher.Root() of an empty byte array
     */
    public static final Hash EMPTY_ROOT_HASH = new Hash(
        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");

    /**
     * Opens a view on the state at the given state root hash.
     *
     * @param db   database instance
     * @param root root hash
     */
    public StateDB(Database db, Hash root) {
        super(LibEvm.invoke("StateOpen", new OpenStateParams(db.handle, root), int.class));
    }

    /**
     * Close this instance and free up any native resources. Must not use this instance afterwards.
     */
    @Override
    public void close() throws Exception {
        LibEvm.invoke("StateClose", new HandleParams(handle));
    }

    /**
     * Finalize any pending changes, clear the current journal and reset refund counter.
     * Note: Must be called between separate transactions as rollbacks are not supported over multiple transactions,
     * therefore also invalidates all snapshots.
     */
    public void finalizeChanges() {
        LibEvm.invoke("StateFinalize", new HandleParams(handle));
    }

    /**
     * Get current state root hash including any currently pending changes, but without committing.
     *
     * @return state root hash
     */
    public Hash getIntermediateRoot() {
        return LibEvm.invoke("StateIntermediateRoot", new HandleParams(handle), Hash.class);
    }

    /**
     * Commit any pending changes. Invalidates all snapshots taken before.
     *
     * @return updated state root hash
     */
    public Hash commit() {
        return LibEvm.invoke("StateCommit", new HandleParams(handle), Hash.class);
    }

    /**
     * Check if the account with the given address is empty
     *
     * @param address account address
     * @return true if account state is empty, otherwise false
     */
    public boolean isEmpty(Address address) {
        return LibEvm.invoke("StateEmpty", new AccountParams(handle, address), boolean.class);
    }

    /**
     * Check if account is an EAO one. Account is considered an EOA in two cases:
     * <ol>
     * <li>account doesn't exist in the StateDB (first time it receives coins);</li>
     * <li>account has no code (code hash is a keccak256 hash of empty array).</li>
     * <li>account address does not match any of the precompiled native contracts.</li>
     * </ol>
     *
     * @param address account address
     * @return true if account is EOA, otherwise false
     */
    public boolean isEoaAccount(Address address) {
        return LibEvm.invoke("StateIsEoa", new AccountParams(handle, address), boolean.class);
    }

    /**
     * Check if an account is a smart contract account
     *
     * @param address account address
     * @return true if account is a smart contract one, otherwise false
     */
    public boolean isSmartContractAccount(Address address) {
        return !isEoaAccount(address);
    }

    /**
     * Get balance of given account.
     *
     * @param address account address
     * @return account balance, 0 if account not exist
     */
    public BigInteger getBalance(Address address) {
        return LibEvm.invoke("StateGetBalance", new AccountParams(handle, address), BigInteger.class);
    }

    /**
     * Add amount to balance of given account. Will implicity create account if necessary.
     *
     * @param address account address
     * @param amount  amount to add to account balance
     */
    public void addBalance(Address address, BigInteger amount) {
        LibEvm.invoke("StateAddBalance", new BalanceParams(handle, address, amount));
    }

    /**
     * Subtract from balance of given account.
     *
     * @param address account address
     * @param amount  amount to subtract from account balance
     */
    public void subBalance(Address address, BigInteger amount) {
        LibEvm.invoke("StateSubBalance", new BalanceParams(handle, address, amount));
    }

    /**
     * Set balance of account balance
     *
     * @param address account address
     * @param amount  amount to assign to the account balance
     */
    public void setBalance(Address address, BigInteger amount) {
        LibEvm.invoke("StateSetBalance", new BalanceParams(handle, address, amount));
    }

    /**
     * Get account nonce.
     *
     * @param address account address
     * @return account nonce
     */
    public BigInteger getNonce(Address address) {
        return LibEvm.invoke("StateGetNonce", new AccountParams(handle, address), BigInteger.class);
    }

    /**
     * Set account nonce.
     *
     * @param address account address
     * @param nonce   value to set account nonce to
     */
    public void setNonce(Address address, BigInteger nonce) {
        LibEvm.invoke("StateSetNonce", new NonceParams(handle, address, nonce));
    }

    /**
     * Get account code hash.
     *
     * @param address account address
     * @return code hash
     */
    public Hash getCodeHash(Address address) {
        return LibEvm.invoke("StateGetCodeHash", new AccountParams(handle, address), Hash.class);
    }

    /**
     * Get code for the given account.
     *
     * @param address account address
     */
    public byte[] getCode(Address address) {
        return LibEvm.invoke("StateGetCode", new AccountParams(handle, address), byte[].class);
    }

    /**
     * Set code for the given account. Will also recalculate and set code hash accordingly.
     *
     * @param address account address
     * @param code    code binary
     */
    public void setCode(Address address, byte[] code) {
        LibEvm.invoke("StateSetCode", new CodeParams(handle, address, code));
    }

    /**
     * Add gas refund.
     *
     * @param gas amount to add to refund counter
     */
    public void addRefund(BigInteger gas) {
        LibEvm.invoke("RefundAdd", new RefundParams(handle, gas), void.class);
    }

    /**
     * Remove gas refund.
     *
     * @param gas amount to remove from refund counter
     */
    public void subRefund(BigInteger gas) {
        LibEvm.invoke("RefundSub", new RefundParams(handle, gas), void.class);
    }

    /**
     * Get refunded gas.
     *
     * @return refunded gas
     */
    public BigInteger getRefund() {
        return LibEvm.invoke("RefundGet", new HandleParams(handle), BigInteger.class);
    }

    /**
     * Read storage trie of given account.
     *
     * @param address account address
     * @param key     storage key
     * @return storage value, always 32 bytes
     */
    public Hash getStorage(Address address, Hash key) {
        return LibEvm.invoke("StateGetStorage", new StorageParams(handle, address, key), Hash.class);
    }

    /**
     * Read comitted storage trie of given account.
     *
     * @param address account address
     * @param key     storage key
     * @return comitted storage value, always 32 bytes
     */
    public Hash getCommittedStorage(Address address, Hash key) {
        return LibEvm.invoke("StateGetCommittedStorage", new StorageParams(handle, address, key), Hash.class);
    }

    /**
     * Write to storage trie of given account.
     * Note: Do not mix RAW and CHUNKED strategies for the same key,
     * this can potentially lead to dangling nodes in the storage Trie and de facto infinite-loops.
     *
     * @param address account address
     * @param key     storage key
     * @param value   value to store
     */
    public void setStorage(Address address, Hash key, Hash value) {
        LibEvm.invoke("StateSetStorage", new SetStorageParams(handle, address, key, value));
    }

    /**
     * Get the Merkle-proof for a given account and optionally some storage keys.
     *
     * @param address     account address
     * @param storageKeys storage keys
     * @return proofs
     */
    public ProofAccountResult getProof(Address address, Hash[] storageKeys) {
        return LibEvm.invoke("StateGetProof", new ProofParams(handle, address, storageKeys), ProofAccountResult.class);
    }

    /**
     * Create a lightweight snapshot at the current state.
     *
     * @return revision id of the snapshot
     */
    public int snapshot() {
        return LibEvm.invoke("StateSnapshot", new HandleParams(handle), int.class);
    }

    /**
     * Rollback all state modifications since the snapshot with the given revision id was created.
     *
     * @param revisionId revision id of the snapshot to revert to
     */
    public void revertToSnapshot(int revisionId) {
        LibEvm.invoke("StateRevertToSnapshot", new SnapshotParams(handle, revisionId));
    }

    /**
     * Get log entries created during the execution of given transaction.
     *
     * @param txHash transaction hash
     * @return log entries related to given transaction hash
     */
    public EvmLog[] getLogs(Hash txHash) {
        return LibEvm.invoke("StateGetLogs", new GetLogsParams(handle, txHash), EvmLog[].class);
    }

    /**
     * Add a new log entry.
     *
     * @param evmLog log entry
     */
    public void addLog(EvmLog evmLog) {
        LibEvm.invoke("StateAddLog", new AddLogParams(handle, evmLog));
    }

    /**
     * Set tx context, used when the EVM emits new state logs.
     *
     * @param txHash  the hash of the transaction to be set in context
     * @param txIndex the index of the transaction in the block
     */
    public void setTxContext(Hash txHash, int txIndex) {
        LibEvm.invoke("StateSetTxContext", new SetTxContextParams(handle, txHash, txIndex));
    }

    /**
     * Reset and prepare account access list.
     *
     * @param sender      sender account
     * @param destination destination account
     */
    public void accessSetup(Address sender, Address destination) {
        LibEvm.invoke("AccessSetup", new AccessParams(handle, sender, destination));
    }

    /**
     * Add the given account to the access list.
     *
     * @param address account to access
     * @return true if the account was already on the access list, false otherwise
     */
    public boolean accessAccount(Address address) {
        return LibEvm.invoke("AccessAccount", new AccountParams(handle, address), Boolean.class);
    }

    /**
     * Add given account storage slot to the access list.
     *
     * @param address account to access
     * @param slot    storage slot to access
     * @return true if the slot was already on the access list, false otherwise
     */
    public boolean accessSlot(Address address, Hash slot) {
        return LibEvm.invoke("AccessSlot", new SlotParams(handle, address, slot), Boolean.class);
    }

    @Override
    public String toString() {
        return String.format("StateDB{handle=%d}", handle);
    }
}
