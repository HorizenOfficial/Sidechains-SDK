package com.horizen.evm;

import com.horizen.evm.library.EvmResult;
import com.horizen.evm.library.LibEvm;
import com.horizen.evm.utils.Converter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class StateDBTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void TestAccountManipulation() throws Exception {
        var databaseFolder = tempFolder.newFolder("evm-db");

        String hashNull = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String hashEmpty = "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421";
        String origin = "0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b";

        LibEvm.openLevelDB(databaseFolder.getAbsolutePath());

        String rootWithBalance1234;
        String rootWithBalance802;
        try (var statedb = new StateDB(hashNull)) {
            var intermediateRoot = statedb.getIntermediateRoot();
            assertEquals(
                "empty state should give the hash of an empty string as the root hash",
                hashEmpty,
                intermediateRoot
            );

            var committedRoot = statedb.commit();
            assertEquals("committed root should equal intermediate root", intermediateRoot, committedRoot);
            assertEquals("0x0", statedb.getBalance(origin));

            statedb.addBalance(origin, "0x1234");
            assertEquals("0x1234", statedb.getBalance(origin));
            assertNotEquals(
                "intermediate root should not equal committed root anymore",
                committedRoot,
                statedb.getIntermediateRoot()
            );
            rootWithBalance1234 = statedb.commit();

            statedb.subBalance(origin, "0x432");
            assertEquals("0xe02", statedb.getBalance(origin));

            assertEquals(0, statedb.getNonce(origin));
            statedb.setNonce(origin, 3);
            assertEquals(3, statedb.getNonce(origin));
            rootWithBalance802 = statedb.commit();

            statedb.setNonce(origin, 5);
            assertEquals(5, statedb.getNonce(origin));
        }
        // verify that automatic resource management worked and StateDB.close() was called
        // if it did, the handle is invalid now and this should throw
        assertThrows(Exception.class, () -> LibEvm.stateIntermediateRoot(1));

        LibEvm.openLevelDB(databaseFolder.getAbsolutePath());

        try (var statedb = new StateDB(rootWithBalance1234)) {
            assertEquals("0x1234", statedb.getBalance(origin));
            assertEquals(0, statedb.getNonce(origin));
        }

        try (var statedb = new StateDB(rootWithBalance802)) {
            assertEquals("0xe02", statedb.getBalance(origin));
            assertEquals(3, statedb.getNonce(origin));
        }

        LibEvm.closeDatabase();
    }

    @Test
    public void TestEvmExecution() throws Exception {
        var databaseFolder = tempFolder.newFolder("evm-db");
        System.out.println("temporary database folder: " + databaseFolder.getAbsolutePath());

        String nullHash = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String codeHash = "0x4a78bbdd842b3867b596c8a29ce62441265c402fdb1ae4b43e33bf21b214f0de";

        String addr1 = "0x1234561234561234561234561234561234561230";
        String addr2 = "0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b";

        String contractCode =
            "608060405234801561001057600080fd5b5060405161015738038061015783398101604081905261002f91610037565b600055610050565b60006020828403121561004957600080fd5b5051919050565b60f98061005e6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea26469706673582212207464e228829f86206a2f85d9740ac1707dac21a7c3790c186c66c62e00bb514664736f6c634300080c0033";
        String initialValue = "0000000000000000000000000000000000000000000000000000000000000000";
        String anotherValue = "00000000000000000000000000000000000000000000000000000000000015b3";

        String funcStore = "6057361d";
        String funcRetrieve = "2e64cec1";

        LibEvm.openMemoryDB();

        String modifiedState;
        EvmResult result;
        String contractAddress;

        try (var statedb = new StateDB(nullHash)) {
            // test a simple value transfer
            statedb.addBalance(addr1, "0xA");
            result = statedb.evmExecute(addr1, addr2, "0x5", null);
            assertEquals("", result.evmError);
            assertEquals("0x5", statedb.getBalance(addr2));
            assertEquals("0x5", statedb.getBalance(addr1));

            // test contract deployment
            result = statedb.evmExecute(addr2, null, null, Converter.fromHexString(contractCode + initialValue));
            assertEquals("", result.evmError);
            contractAddress = result.contractAddress;
            assertEquals(codeHash, statedb.getCodeHash(contractAddress));

            // call "store" function on the contract to set a value
            result = statedb.evmExecute(addr2, contractAddress, null, Converter.fromHexString(funcStore + anotherValue));
            assertEquals("", result.evmError);

            // call "retrieve" on the contract to fetch the value we just set
            result = statedb.evmExecute(addr2, contractAddress, null, Converter.fromHexString(funcRetrieve));
            assertEquals("", result.evmError);
            var returnValue = Converter.toHexString(result.returnData);
            assertEquals(anotherValue, returnValue);

            modifiedState = statedb.commit();
        }

        // reopen the state and retrieve a value
        try (var statedb = new StateDB(modifiedState)) {
            result = statedb.evmExecute(addr2, contractAddress, null, Converter.fromHexString(funcRetrieve));
            assertEquals("", result.evmError);
            var returnValue = Converter.toHexString(result.returnData);
            assertEquals(anotherValue, returnValue);
        }

        LibEvm.closeDatabase();
    }
}
