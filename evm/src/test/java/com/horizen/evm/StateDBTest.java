package com.horizen.evm;

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

        LibEvm.Initialize(databaseFolder.getAbsolutePath());

        String rootWithBalance1234;
        String rootWithBalance802;
        try (var statedb = new StateDB(hashNull)) {
            var intermediateRoot = statedb.GetIntermediateRoot();
            assertEquals(
                "empty state should give the hash of an empty string as the root hash",
                intermediateRoot,
                hashEmpty
            );

            var committedRoot = statedb.Commit();
            assertEquals("committed root should equal intermediate root", committedRoot, intermediateRoot);
            assertEquals(statedb.GetBalance(origin), "0");

            statedb.AddBalance(origin, "1234");
            assertEquals(statedb.GetBalance(origin), "1234");
            assertNotEquals(
                "intermediate root should not equal committed root anymore",
                statedb.GetIntermediateRoot(),
                committedRoot
            );
            rootWithBalance1234 = statedb.Commit();

            statedb.SubBalance(origin, "432");
            assertEquals(statedb.GetBalance(origin), "802");
            rootWithBalance802 = statedb.Commit();

            assertEquals(statedb.GetNonce(origin), 0);
            statedb.SetNonce(origin, 3);
            assertEquals(statedb.GetNonce(origin), 3);
        }
        // verify that automatic resource management worked and StateDB.close() was called,
        // if it did the handle is invalid now and this should throw
        assertThrows(Exception.class, () -> LibEvm.StateIntermediateRoot(1));

        try (var statedb = new StateDB(rootWithBalance1234)) {
            assertEquals(statedb.GetBalance(origin), "1234");
        }

        try (var statedb = new StateDB(rootWithBalance802)) {
            assertEquals(statedb.GetBalance(origin), "802");
        }

        LibEvm.CloseDatabase();
    }

    @Test
    public void TestEvmExecution() throws Exception {
        var databaseFolder = tempFolder.newFolder("evm-db");
        System.out.println("temporary database folder: " + databaseFolder.getAbsolutePath());

        String hashNull = "0x0000000000000000000000000000000000000000000000000000000000000000";
        String faucet = "0x1234561234561234561234561234561234561230";
        String user = "0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b";
        String contractCode =
            "608060405234801561001057600080fd5b5060405161015738038061015783398101604081905261002f91610037565b600055610050565b60006020828403121561004957600080fd5b5051919050565b60f98061005e6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea26469706673582212207464e228829f86206a2f85d9740ac1707dac21a7c3790c186c66c62e00bb514664736f6c634300080c0033";
        String initialValue = "0000000000000000000000000000000000000000000000000000000000000000";
        String secondValue = "00000000000000000000000000000000000000000000000000000000000015b3";
        String funcStore = "6057361d";
        String funcRetrieve = "2e64cec1";

        LibEvm.Initialize(databaseFolder.getAbsolutePath());

        String modifiedState;
        LibEvm.EvmResult result;
        String contractAddress;

        try (var statedb = new StateDB(hashNull)) {
            // test a simple value transfer
            statedb.AddBalance(faucet, "10");
            result = statedb.EvmExecute(faucet, user, "5", null);
            assertEquals(result.evmError, "");
            assertEquals(statedb.GetBalance(user), "5");
            assertEquals(statedb.GetBalance(faucet), "5");

            // test contract deployment
            result = statedb.EvmExecute(user, null, null, Converter.fromHexString(contractCode + initialValue));
            assertEquals(result.evmError, "");
            contractAddress = result.address;
            System.out.println("contract create result: " + contractAddress);

            // call "store" function on the contract to set a value
            result = statedb.EvmExecute(user, contractAddress, null, Converter.fromHexString(funcStore + secondValue));
            assertEquals(result.evmError, "");

            // call "retrieve" on the contract to fetch the value we just set
            result = statedb.EvmExecute(user, contractAddress, null, Converter.fromHexString(funcRetrieve));
            assertEquals(result.evmError, "");
            var returnValue = Converter.toHexString(result.returnData);
            assertEquals(returnValue, secondValue);

            modifiedState = statedb.Commit();
        }

        // reopen the state and retrieve a value
        try (var statedb = new StateDB(modifiedState)) {
            result = statedb.EvmExecute(user, contractAddress, null, Converter.fromHexString(funcRetrieve));
            assertEquals(result.evmError, "");
            var returnValue = Converter.toHexString(result.returnData);
            assertEquals(returnValue, secondValue);
        }

        LibEvm.CloseDatabase();
    }
}
