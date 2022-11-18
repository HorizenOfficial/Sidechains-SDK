package com.horizen.evm;

import com.horizen.evm.utils.Converter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class StateDBTest extends LibEvmTestBase {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void accountManipulation() throws Exception {
        final var databaseFolder = tempFolder.newFolder("evm-db");

        final var origin = bytes("bafe3b6f2a19658df3cb5efca158c93272ff5c0b");

        final var v1234 = BigInteger.valueOf(1234);
        final var v432 = BigInteger.valueOf(432);
        final var v802 = v1234.subtract(v432);
        final var v3 = BigInteger.valueOf(3);
        final var v5 = BigInteger.valueOf(5);

        byte[] rootWithBalance1234;
        byte[] rootWithBalance802;

        try (var db = new LevelDBDatabase(databaseFolder.getAbsolutePath())) {
            try (var statedb = new StateDB(db, hashNull)) {
                var intermediateRoot = statedb.getIntermediateRoot();
                assertArrayEquals(
                        "empty state should give the hash of an empty string as the root hash",
                        hashEmpty,
                        intermediateRoot
                );

                var committedRoot = statedb.commit();
                assertArrayEquals("committed root should equal intermediate root", intermediateRoot, committedRoot);
                assertEquals(BigInteger.ZERO, statedb.getBalance(origin));

                statedb.addBalance(origin, v1234);
                assertEquals(v1234, statedb.getBalance(origin));
                assertNotEquals(
                        "intermediate root should not equal committed root anymore",
                        committedRoot,
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
            assertThrows(Exception.class, () -> LibEvm.stateIntermediateRoot(1));
        }
        // also verify that the database was closed
        assertThrows(Exception.class, () -> LibEvm.stateOpen(1, hashNull));

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
        final var origin = bytes("bafe3b6f2a19658df3cb5efca158c93272ff5cff");
        final var key = bytes("bafe3b6f2a19658df3cb5efca158c93272ff5cff010101010101010102020202");
        final var fakeCodeHash = bytes("abcdef00000000000000000000000000000000ff010101010101010102020202");
        final byte[][] values = {
                bytes("aa"),
                bytes("ffff"),
                bytes("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"),
                bytes("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeffabcd001122"),
                bytes("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"),
                bytes("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeffaa"),
        };

        byte[] initialRoot;
        var roots = new ArrayList<byte[]>();

        try (var db = new LevelDBDatabase(databaseFolder.getAbsolutePath())) {
            try (var statedb = new StateDB(db, hashEmpty)) {
                assertTrue("account must not exist in an empty state", statedb.isEmpty(origin));
                // make sure the account is not "empty"
                statedb.setCodeHash(origin, fakeCodeHash);
                assertFalse("account must exist after setting code hash", statedb.isEmpty(origin));
                initialRoot = statedb.getIntermediateRoot();
                for (var value : values) {
                    statedb.setStorage(origin, key, value, StateStorageStrategy.CHUNKED);
                    var retrievedValue = statedb.getStorage(origin, key, StateStorageStrategy.CHUNKED);
                    assertArrayEquals(value, retrievedValue);
                    // store the root hash of each state
                    roots.add(statedb.commit());
                    var committedValue = statedb.getStorage(origin, key, StateStorageStrategy.CHUNKED);
                    assertArrayEquals(value, committedValue);
                }
            }
        }

        // verify that every committed state can be loaded again and that the stored values are still as expected
        try (var db = new LevelDBDatabase(databaseFolder.getAbsolutePath())) {
            for (int i = 0; i < values.length; i++) {
                try (var statedb = new StateDB(db, roots.get(i))) {
                    var writtenValue = statedb.getStorage(origin, key, StateStorageStrategy.CHUNKED);
                    assertArrayEquals(values[i], writtenValue);
                    // verify that removing the key results in the initial state root
                    statedb.removeStorage(origin, key, StateStorageStrategy.CHUNKED);
                    assertArrayEquals(initialRoot, statedb.getIntermediateRoot());
                }
            }
        }
    }

    @Test
    public void accountTypes() throws Exception {
        final byte[] codeHash =
                Converter.fromHexString("aa87aee0394326416058ef46b907882903f3646ef2a6d0d20f9e705b87c58c77");
        final byte[] addr1 = Converter.fromHexString("1234561234561234561234561234561234561230");

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, hashNull)) {
                // Test 1: non-existing account is an EOA account
                assertTrue("EOA account expected", statedb.isEoaAccount(addr1));
                assertFalse("EOA account expected", statedb.isSmartContractAccount(addr1));


                // Test 2: account exists and has NO code defined, so considered as EOA
                // Declare account with some coins
                statedb.addBalance(addr1, BigInteger.TEN);
                assertTrue("EOA account expected", statedb.isEoaAccount(addr1));
                assertFalse("EOA account expected", statedb.isSmartContractAccount(addr1));

                // Test 3: Account exists and has code defined, so considered as Smart contract account
                statedb.setCodeHash(addr1, codeHash);
                assertFalse("Smart contract account expected", statedb.isEoaAccount(addr1));
                assertTrue("Smart contract account expected", statedb.isSmartContractAccount(addr1));
            }
        }
    }

    @Test
    public void proof() throws Exception {
        final var address = bytes("1afe484cb38eF97A5Ab6b461AD60dc9941714aFF");
        final var fakeCodeHash = bytes("abcdef00000000000000000000000000000000ff010101010101010102020202");

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, hashNull)) {
                var proofWithoutStorage = statedb.getProof(address, new byte[0][0]);
                assertArrayEquals(proofWithoutStorage.accountProof, new String[0]);

                statedb.setCodeHash(address, fakeCodeHash);
                statedb.commit();

                var proofWithStorage = statedb.getProof(address, new byte[0][0]);
                assertNotSame("Code hashes shouldn't match", proofWithStorage.codeHash, proofWithoutStorage.codeHash);
                assertTrue("Proof shouldn't be empty", proofWithStorage.accountProof.length != 0);
            }
        }
    }
}
