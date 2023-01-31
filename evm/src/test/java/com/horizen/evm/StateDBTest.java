package com.horizen.evm;

import com.horizen.evm.interop.HandleParams;
import com.horizen.evm.interop.OpenStateParams;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpString;
import scorex.crypto.hash.Keccak256;

import java.math.BigInteger;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class StateDBTest extends LibEvmTestBase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void accountManipulation() throws Exception {
        final var databaseFolder = tempFolder.newFolder("evm-db");

        final var origin = address("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b");

        final var v1234 = BigInteger.valueOf(1234);
        final var v432 = BigInteger.valueOf(432);
        final var v802 = v1234.subtract(v432);
        final var v3 = BigInteger.valueOf(3);
        final var v5 = BigInteger.valueOf(5);

        Hash rootWithBalance1234;
        Hash rootWithBalance802;

        try (var db = new LevelDBDatabase(databaseFolder.getAbsolutePath())) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                var intermediateRoot = statedb.getIntermediateRoot();
                assertEquals(
                    "empty state should give the hash of an empty string as the root hash",
                    StateDB.EMPTY_ROOT_HASH,
                    intermediateRoot
                );

                var committedRoot = statedb.commit();
                assertEquals("committed root should equal intermediate root", intermediateRoot, committedRoot);
                assertEquals(BigInteger.ZERO, statedb.getBalance(origin));

                statedb.addBalance(origin, v1234);
                assertEquals(v1234, statedb.getBalance(origin));
                assertNotEquals("intermediate root should not equal committed root anymore", committedRoot,
                    statedb.getIntermediateRoot()
                );
                rootWithBalance1234 = statedb.commit();

                var revisionId = statedb.snapshot();
                statedb.subBalance(origin, v432);
                assertEquals(v802, statedb.getBalance(origin));
                statedb.revertToSnapshot(revisionId);
                assertEquals(v1234, statedb.getBalance(origin));
                statedb.subBalance(origin, v432);
                assertEquals(v802, statedb.getBalance(origin));

                assertEquals(BigInteger.ZERO, statedb.getNonce(origin));
                statedb.setNonce(origin, v3);
                assertEquals(v3, statedb.getNonce(origin));
                rootWithBalance802 = statedb.commit();

                statedb.setNonce(origin, v5);
                assertEquals(v5, statedb.getNonce(origin));
            }
            // Verify that automatic resource management worked and StateDB.close() was called.
            // If it was, the handle is invalid now and this should throw.
            assertThrows(
                Exception.class,
                () -> LibEvm.invoke("StateIntermediateRoot", new HandleParams(1), Hash.class).toBytes()
            );
        }
        // also verify that the database was closed
        assertThrows(
            Exception.class,
            () -> LibEvm.invoke("StateOpen", new OpenStateParams(1, Hash.ZERO), int.class)
        );

        try (var db = new LevelDBDatabase(databaseFolder.getAbsolutePath())) {
            try (var statedb = new StateDB(db, rootWithBalance1234)) {
                assertEquals(v1234, statedb.getBalance(origin));
                assertEquals(BigInteger.ZERO, statedb.getNonce(origin));
            }

            try (var statedb = new StateDB(db, rootWithBalance802)) {
                assertEquals(v802, statedb.getBalance(origin));
                assertEquals(v3, statedb.getNonce(origin));
            }
        }
    }

    @Test
    public void accountStorage() throws Exception {
        final var databaseFolder = tempFolder.newFolder("account-db");
        final var origin = address("0xbafe3b6f2a19658df3cb5efca158c93272ff5cff");
        final var key = new Hash("0xbafe3b6f2a19658df3cb5efca158c93272ff5cff010101010101010102020202");
        final Hash[] values = {
            new Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
            new Hash("0x0000000000000000000000001234000000000000000000000000000000000000"),
            new Hash("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
            new Hash("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"),
        };

        Hash initialRoot;
        var roots = new ArrayList<Hash>();

        try (var db = new LevelDBDatabase(databaseFolder.getAbsolutePath())) {
            try (var statedb = new StateDB(db, StateDB.EMPTY_ROOT_HASH)) {
                assertTrue("account must not exist in an empty state", statedb.isEmpty(origin));
                // make sure the account is not "empty"
                statedb.setNonce(origin, BigInteger.ONE);
                assertFalse("account must exist after nonce increment", statedb.isEmpty(origin));
                initialRoot = statedb.getIntermediateRoot();
                for (var value : values) {
                    statedb.setStorage(origin, key, value);
                    var retrievedValue = statedb.getStorage(origin, key);
                    assertEquals(value, retrievedValue);
                    // store the root hash of each state
                    roots.add(statedb.commit());
                    var committedValue = statedb.getStorage(origin, key);
                    assertEquals(value, committedValue);
                }
            }
        }

        // verify that every committed state can be loaded again and that the stored values are still as expected
        try (var db = new LevelDBDatabase(databaseFolder.getAbsolutePath())) {
            for (int i = 0; i < values.length; i++) {
                try (var statedb = new StateDB(db, roots.get(i))) {
                    var writtenValue = statedb.getStorage(origin, key);
                    assertEquals(values[i], writtenValue);
                    // verify that removing the key results in the initial state root
                    statedb.setStorage(origin, key, null);
                    assertEquals(initialRoot, statedb.getIntermediateRoot());
                }
            }
        }
    }

    @Test
    public void accountStorageEdgeCases() throws Exception {
        final var origin = address("0xbafe3b6f2a19658df3cb5efca158c93272ff5cff");
        final var key = new Hash("0xbafe3b6f2a19658df3cb5efca158c93272ff5cff010101010101010102020202");
        // test some negative cases:
        // - trying to store a value that is not 32 bytes should throw - after refactoring to "Hash" this is prevented
        // - writing 32 bytes of zeros and null should behave identical (remove the key-value pair)
        final Hash validValue = new Hash("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, StateDB.EMPTY_ROOT_HASH)) {
                assertTrue("account must not exist in an empty state", statedb.isEmpty(origin));
                // writing to an "empty" account should fail:
                // this is a safety precaution, because empty accounts will be pruned, even if the storage is not empty
                assertThrows(InvokeException.class, () -> statedb.setStorage(origin, key, validValue));
                // make sure the account is not "empty"
                statedb.setNonce(origin, BigInteger.ONE);
                assertEquals(
                    "reading a non-existent key should return all zeroes",
                    Hash.ZERO,
                    statedb.getStorage(origin, key)
                );
                // make sure this does not throw anymore and the value can be read correctly
                statedb.setStorage(origin, key, validValue);
                assertEquals("value was not written correctly", validValue, statedb.getStorage(origin, key));
                // make sure the value did not change after invalid write attempts
                assertEquals("unexpected change of written value", validValue, statedb.getStorage(origin, key));
                // test removal of the key by using null
                statedb.setStorage(origin, key, null);
                assertEquals("value should be all zeroes", Hash.ZERO, statedb.getStorage(origin, key));
                // write the value again
                statedb.setStorage(origin, key, validValue);
                // test removal of the key by using all zeros
                statedb.setStorage(origin, key, Hash.ZERO);
                assertEquals("value should be all zeroes", Hash.ZERO, statedb.getStorage(origin, key));
            }
        }
    }

    private void testAccessListAccounts(StateDB statedb, Address sender, Address destination, Address other) {
        final var key1 = new Hash("0xbafe3b6f2a19658df3cb5efca158c93272ff5cff000000000000000000000001");
        final var key2 = new Hash("0xbafe3b6f2a19658df3cb5efca158c93272ff5cff000000000000000000000002");

        statedb.accessSetup(sender, destination);
        assertTrue("sender must be on access list", statedb.accessAccount(sender));
        assertTrue("destination must be on access list", statedb.accessAccount(destination));
        assertFalse(
            "sender storage slot must not be on access list before first access",
            statedb.accessSlot(sender, key1)
        );
        assertTrue(
            "sender storage slot must be on access list after first access",
            statedb.accessSlot(sender, key1)
        );
        assertFalse(
            "sender storage slot must not be on access list before first access",
            statedb.accessSlot(sender, key2)
        );
        assertTrue(
            "sender storage slot must be on access list after first access",
            statedb.accessSlot(sender, key2)
        );

        assertFalse(
            "other account must not be on access list before first access",
            statedb.accessAccount(other)
        );
        assertTrue(
            "other account must be on access list after first acccess",
            statedb.accessAccount(other)
        );
        assertFalse(
            "other storage slot must not be on access list before first access",
            statedb.accessSlot(other, key1)
        );
        assertTrue(
            "other storage slot must be on access list after first access",
            statedb.accessSlot(other, key1)
        );
    }

    @Test
    public void accessList() throws Exception {
        final var accounts = new Address[] {
            address("0x0011001100110011001100110011001100110011"),
            address("0x0022002200220022002200220022002200220022"),
            address("0x0033003300330033003300330033003300330033"),
        };

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, StateDB.EMPTY_ROOT_HASH)) {
                // test multiple permutations of the accounts in a row to make sure the access list is correctly reset
                testAccessListAccounts(statedb, accounts[0], accounts[1], accounts[2]);
                testAccessListAccounts(statedb, accounts[1], accounts[2], accounts[0]);
                testAccessListAccounts(statedb, accounts[2], accounts[0], accounts[1]);
                testAccessListAccounts(statedb, accounts[0], accounts[2], accounts[1]);
                testAccessListAccounts(statedb, accounts[1], accounts[0], accounts[2]);
            }
        }
    }

    @Test
    public void TestAccountTypes() throws Exception {
        final var code = bytes("aa87aee0394326416058ef46b907882903f3646ef2a6d0d20f9e705b87c58c77");
        final var addr1 = address("0x1234561234561234561234561234561234561230");

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                // Test 1: non-existing account is an EOA account
                assertTrue("EOA account expected", statedb.isEoaAccount(addr1));
                assertFalse("EOA account expected", statedb.isSmartContractAccount(addr1));

                // Test 2: account exists and has NO code defined, so considered as EOA
                // Declare account with some coins
                statedb.addBalance(addr1, BigInteger.TEN);
                assertTrue("EOA account expected", statedb.isEoaAccount(addr1));
                assertFalse("EOA account expected", statedb.isSmartContractAccount(addr1));

                // Test 3: Account exists and has code defined, so considered as Smart contract account
                statedb.setCode(addr1, code);
                assertFalse("Smart contract account expected", statedb.isEoaAccount(addr1));
                assertTrue("Smart contract account expected", statedb.isSmartContractAccount(addr1));
            }
        }
    }

    @Test
    @Ignore
    public void proof() throws Exception {
        final var address = address("cca577ee56d30a444c73f8fc8d5ce34ed1c7da8b");

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                statedb.setStorage(
                    address,
                    new Hash((byte[]) Keccak256.hash(Hash.ZERO.toBytes())),
                    padToHash(RlpEncoder.encode(RlpString.create(bytes("94de74da73d5102a796559933296c73e7d1c6f37fb"))))
                );
                statedb.setStorage(
                    address,
                    new Hash((byte[]) Keccak256.hash(
                        bytes("0000000000000000000000000000000000000000000000000000000000000001"))),
                    padToHash(RlpEncoder.encode(RlpString.create(bytes("02"))))
                );

                statedb.commit();

                // this should return the proof for the 0th slot in smart contract identified by address
                // storageProof's length is always 0
                var proofAccountResult = statedb.getProof(
                    address,
                    new Hash[] {Hash.ZERO}
                );

                // after successful proof retrieval, we should verify the root hash
            }
        }
    }
}
