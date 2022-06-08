package com.horizen.evm;

import com.horizen.evm.interop.EvmResult;
import com.horizen.evm.utils.Converter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class StateDBTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    static final byte[] hashNull =
            Converter.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
    static final byte[] hashEmpty =
            Converter.fromHexString("56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");

    @Test
    public void TestAccountManipulation() throws Exception {
        final var databaseFolder = tempFolder.newFolder("evm-db");

        final byte[] origin = Converter.fromHexString("bafe3b6f2a19658df3cb5efca158c93272ff5c0b");

        final BigInteger v1234 = BigInteger.valueOf(1234);
        final BigInteger v432 = BigInteger.valueOf(432);
        final BigInteger v802 = v1234.subtract(v432);

        Database.openLevelDB(databaseFolder.getAbsolutePath());

        byte[] rootWithBalance1234;
        byte[] rootWithBalance802;
        try (var statedb = new StateDB(hashNull)) {
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

            statedb.subBalance(origin, v432);
            assertEquals(v802, statedb.getBalance(origin));

            assertEquals(0, statedb.getNonce(origin));
            statedb.setNonce(origin, 3);
            assertEquals(3, statedb.getNonce(origin));
            rootWithBalance802 = statedb.commit();

            statedb.setNonce(origin, 5);
            assertEquals(5, statedb.getNonce(origin));
        }
        // Verify that automatic resource management worked and StateDB.close() was called.
        // If it was, the handle is invalid now and this should throw.
        assertThrows(Exception.class, () -> LibEvm.stateIntermediateRoot(1));

        Database.openLevelDB(databaseFolder.getAbsolutePath());

        try (var statedb = new StateDB(rootWithBalance1234)) {
            assertEquals(v1234, statedb.getBalance(origin));
            assertEquals(0, statedb.getNonce(origin));
        }

        try (var statedb = new StateDB(rootWithBalance802)) {
            assertEquals(v802, statedb.getBalance(origin));
            assertEquals(3, statedb.getNonce(origin));
        }

        Database.closeDatabase();
    }

    @Test
    public void TestAccountStorage() throws Exception {
        final var databaseFolder = tempFolder.newFolder("account-db");
        final byte[] origin = Converter.fromHexString("bafe3b6f2a19658df3cb5efca158c93272ff5cff");
        final byte[] key = Converter.fromHexString("bafe3b6f2a19658df3cb5efca158c93272ff5cff010101010101010102020202");
        final byte[][] values = {
                Converter.fromHexString("aa"),
                Converter.fromHexString("ffff"),
                Converter.fromHexString("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"),
                Converter.fromHexString("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeffaa"),
                Converter.fromHexString("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"),
                Converter.fromHexString("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeffabcd001122"),
        };

        var roots = new ArrayList<byte[]>();

        Database.openLevelDB(databaseFolder.getAbsolutePath());
        try (var statedb = new StateDB(hashEmpty)) {
            // make sure the account is not "empty"
            statedb.setNonce(origin, 1);
            for (byte[] value : values) {
                statedb.setStorageBytes(origin, key, value);
                var retrievedValue = statedb.getStorageBytes(origin, key);
                assertArrayEquals(value, retrievedValue);
                // store the root hash of each state
                roots.add(statedb.commit());
                var committedValue = statedb.getStorageBytes(origin, key);
                assertArrayEquals(value, committedValue);
            }
        }
        Database.closeDatabase();

        // verify that every committed state can be loaded again and that the stored values are still as expected
        Database.openLevelDB(databaseFolder.getAbsolutePath());
        for (int i = 0; i < values.length; i++) {
            try (var statedb = new StateDB(roots.get(i))) {
                var writtenValue = statedb.getStorageBytes(origin, key);
                assertArrayEquals(values[i], writtenValue);
            }
        }
        Database.closeDatabase();
    }

    @Test
    public void TestEvmExecution() throws Exception {
        final byte[] codeHash = Converter.fromHexString("aa87aee0394326416058ef46b907882903f3646ef2a6d0d20f9e705b87c58c77");
        final byte[] addr1 = Converter.fromHexString("1234561234561234561234561234561234561230");
        final byte[] addr2 = Converter.fromHexString("bafe3b6f2a19658df3cb5efca158c93272ff5c0b");

        final String contractCode =
                "608060405234801561001057600080fd5b5060405161023638038061023683398101604081905261002f916100f6565b6000819055604051339060008051602061021683398151915290610073906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03166000805160206102168339815191526040516100bf906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b9994690600090a15061010f565b60006020828403121561010857600080fd5b5051919050565b60f98061011d6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea2646970667358221220769e4dd8320afae06d27e8e201c885728883af2ea321d02071c47704c1b3c24f64736f6c634300080e00330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0";
        final String initialValue = "0000000000000000000000000000000000000000000000000000000000000000";
        final String anotherValue = "00000000000000000000000000000000000000000000000000000000000015b3";

        final String funcStore = "6057361d";
        final String funcRetrieve = "2e64cec1";

        final BigInteger v10 = BigInteger.valueOf(10);
        final BigInteger v5 = BigInteger.valueOf(5);

        Database.openMemoryDB();

        EvmResult result;
        byte[] contractAddress;
        byte[] modifiedStateRoot;

        try (var statedb = new StateDB(hashNull)) {
            // test a simple value transfer
            statedb.addBalance(addr1, v10);
            result = Evm.Execute(statedb, addr1, addr2, v5, null);
            assertEquals("", result.evmError);
            assertEquals(v5, statedb.getBalance(addr2));
            assertEquals(v5, statedb.getBalance(addr1));

            // test contract deployment
            result = Evm.Execute(statedb, addr2, null, null, Converter.fromHexString(contractCode + initialValue));
            assertEquals("", result.evmError);
            contractAddress = result.contractAddress.toBytes();
            assertArrayEquals(codeHash, statedb.getCodeHash(contractAddress));

            // call "store" function on the contract to set a value
            result =
                    Evm.Execute(statedb, addr2, contractAddress, null, Converter.fromHexString(funcStore + anotherValue));
            assertEquals("", result.evmError);

            // call "retrieve" on the contract to fetch the value we just set
            result = Evm.Execute(statedb, addr2, contractAddress, null, Converter.fromHexString(funcRetrieve));
            assertEquals("", result.evmError);
            var returnValue = Converter.toHexString(result.returnData);
            assertEquals(anotherValue, returnValue);

            modifiedStateRoot = statedb.commit();
        }

        // reopen the state and retrieve a value
        try (var statedb = new StateDB(modifiedStateRoot)) {
            result = Evm.Execute(statedb, addr2, contractAddress, null, Converter.fromHexString(funcRetrieve));
            assertEquals("", result.evmError);
            var returnValue = Converter.toHexString(result.returnData);
            assertEquals(anotherValue, returnValue);
        }

        Database.closeDatabase();
    }
}
